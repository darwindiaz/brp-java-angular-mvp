package co.brp.identity.presentation;

import java.util.List;

import co.brp.identity.application.IdentityAdministration;
import co.brp.identity.domain.IdentityUser;
import co.brp.identity.domain.IdentityUser.Module;
import co.brp.identity.domain.IdentityUser.Role;
import co.brp.identity.infrastructure.IdentityStore.ModuleOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('MODULE_ADMINISTRACION')")
class UserAdministrationController {

    private final IdentityAdministration administration;

    UserAdministrationController(IdentityAdministration administration) {
        this.administration = administration;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    IdentityUser create(@AuthenticationPrincipal IdentityUser actor, @Valid @RequestBody CreateUser request) {
        return administration.create(actor.id(), request.name(), request.login(), request.password(), request.role());
    }

    @GetMapping("/users")
    List<IdentityUser> users() {
        return administration.users();
    }

    @GetMapping("/users/{id}")
    IdentityUser user(@PathVariable long id) {
        return administration.user(id);
    }

    @PatchMapping("/users/{id}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeRole(@AuthenticationPrincipal IdentityUser actor, @PathVariable long id,
                    @Valid @RequestBody ChangeRole request) {
        administration.changeRole(actor.id(), id, request.role());
    }

    @PatchMapping("/users/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeStatus(@AuthenticationPrincipal IdentityUser actor, @PathVariable long id,
                      @Valid @RequestBody ChangeStatus request) {
        administration.changeStatus(actor.id(), id, request.active());
    }

    @GetMapping("/modules")
    List<ModuleOption> modules() {
        return administration.modules();
    }

    @GetMapping("/users/{id}/modules")
    List<Module> assignedModules(@PathVariable long id) {
        return administration.user(id).modules();
    }

    @PutMapping("/users/{id}/modules/{module}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void grant(@AuthenticationPrincipal IdentityUser actor, @PathVariable long id, @PathVariable Module module) {
        administration.changeModule(actor.id(), id, module, true);
    }

    @DeleteMapping("/users/{id}/modules/{module}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal IdentityUser actor, @PathVariable long id, @PathVariable Module module) {
        administration.changeModule(actor.id(), id, module, false);
    }

    record CreateUser(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 100) String login,
                      @NotBlank @Size(min = 8, max = 72) String password, @NotNull Role role) {
        @Override
        public String toString() {
            return "CreateUser[credentials=redacted]";
        }
    }

    record ChangeRole(@NotNull Role role) {
    }

    record ChangeStatus(@NotNull Boolean active) {
    }
}
