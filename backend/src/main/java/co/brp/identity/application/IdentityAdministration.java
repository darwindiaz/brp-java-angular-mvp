package co.brp.identity.application;

import java.nio.charset.StandardCharsets;
import java.util.List;

import co.brp.identity.domain.IdentityUser;
import co.brp.identity.domain.IdentityUser.Module;
import co.brp.identity.domain.IdentityUser.Role;
import co.brp.identity.infrastructure.IdentityStore;
import co.brp.identity.infrastructure.IdentityStore.ModuleOption;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityAdministration {

    private final IdentityStore store;
    private final PasswordEncoder passwords;

    public IdentityAdministration(IdentityStore store, PasswordEncoder passwords) {
        this.store = store;
        this.passwords = passwords;
    }

    public List<IdentityUser> users() {
        return store.list();
    }

    public IdentityUser user(long id) {
        return store.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario inexistente."));
    }

    public List<ModuleOption> modules() {
        return store.listModules();
    }

    @Transactional
    public IdentityUser create(long actor, String name, String login, String password, Role role) {
        var normalized = IdentityUser.normalizeLogin(login);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,79}")
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador o contraseña fuera del formato permitido.");
        }
        var hash = passwords.encode(password);
        lockAndAuthorize(actor);
        try {
            return user(store.create(name.strip(), normalized, hash, role));
        } catch (DuplicateKeyException exception) {
            throw conflict("El identificador de acceso ya existe.");
        }
    }

    @Transactional
    public void changeRole(long actor, long id, Role role) {
        lockAndAuthorize(actor);
        if (user(id).role() == role) {
            return;
        }
        if (actor == id && role != Role.ADMINISTRADOR) {
            throw conflict("No puedes retirar tu propio rol administrativo.");
        }
        store.changeRole(id, role);
        requireEffectiveAdministrator();
    }

    @Transactional
    public void changeStatus(long actor, long id, boolean active) {
        lockAndAuthorize(actor);
        if (user(id).active() == active) {
            return;
        }
        if (actor == id && !active) {
            throw conflict("No puedes desactivar tu propia cuenta administrativa.");
        }
        store.changeStatus(id, active);
        requireEffectiveAdministrator();
    }

    @Transactional
    public void changeModule(long actor, long id, Module module, boolean grant) {
        lockAndAuthorize(actor);
        user(id);
        if (!grant && actor == id && module == Module.ADMINISTRACION) {
            throw conflict("No puedes retirar tu propio módulo de Administración.");
        }
        if (store.changeModule(id, module, grant)) {
            requireEffectiveAdministrator();
            store.auditModule(actor, id, module, grant);
        }
    }

    private void lockAndAuthorize(long actor) {
        store.lockAdministration();
        if (store.find(actor).filter(IdentityUser::canAdminister).isEmpty()) {
            throw new AccessDeniedException("Acceso administrativo no vigente.");
        }
    }

    private void requireEffectiveAdministrator() {
        if (!store.hasEffectiveAdministrator()) {
            throw conflict("Debe permanecer al menos un administrador efectivo.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
