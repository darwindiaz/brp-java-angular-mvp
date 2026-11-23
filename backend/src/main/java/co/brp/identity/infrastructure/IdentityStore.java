package co.brp.identity.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import co.brp.identity.domain.IdentityUser;
import co.brp.identity.domain.IdentityUser.Module;
import co.brp.identity.domain.IdentityUser.Role;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityStore {

    private static final String USERS = """
            SELECT u.id, u.nombre, u.identificador_acceso, u.rol, u.activo, u.creado_en, u.actualizado_en,
                   ARRAY(SELECT modulo_codigo FROM usuario_modulo WHERE usuario_id = u.id ORDER BY modulo_codigo) AS modulos
            FROM usuario u
            """;
    private final JdbcClient jdbc;

    public IdentityStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<IdentityUser> find(long id) {
        return jdbc.sql(USERS + " WHERE u.id = :id").param("id", id).query(IdentityStore::mapUser).optional();
    }

    public List<IdentityUser> list() {
        return jdbc.sql(USERS + " ORDER BY u.id").query(IdentityStore::mapUser).list();
    }

    public Optional<Credentials> credentials(String login) {
        return jdbc.sql("""
                SELECT id, password_hash, activo FROM usuario WHERE identificador_acceso = :login
                """).param("login", login)
                .query((rs, row) -> new Credentials(rs.getLong(1), rs.getString(2), rs.getBoolean(3))).optional();
    }

    public long create(String name, String login, String hash, Role role) {
        return jdbc.sql("""
                INSERT INTO usuario (nombre, identificador_acceso, password_hash, rol)
                VALUES (:name, :login, :hash, :role) RETURNING id
                """).param("name", name).param("login", login).param("hash", hash).param("role", role.name())
                .query(Long.class).single();
    }

    public void lockAdministration() {
        jdbc.sql("SELECT id FROM bloqueo_administracion WHERE id = 1 FOR UPDATE").query(Integer.class).single();
    }

    public boolean hasEffectiveAdministrator() {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM usuario u JOIN usuario_modulo m ON m.usuario_id = u.id
                    WHERE u.activo AND u.rol = 'ADMINISTRADOR' AND m.modulo_codigo = 'ADMINISTRACION'
                )
                """).query(Boolean.class).single();
    }

    public void changeRole(long id, Role role) {
        jdbc.sql("UPDATE usuario SET rol = :value, actualizado_en = clock_timestamp() WHERE id = :id")
                .param("id", id).param("value", role.name()).update();
    }

    public void changeStatus(long id, boolean active) {
        jdbc.sql("UPDATE usuario SET activo = :value, actualizado_en = clock_timestamp() WHERE id = :id")
                .param("id", id).param("value", active).update();
    }

    public boolean changeModule(long id, Module module, boolean grant) {
        var sql = grant
                ? "INSERT INTO usuario_modulo (usuario_id, modulo_codigo) VALUES (:id, :module) ON CONFLICT DO NOTHING"
                : "DELETE FROM usuario_modulo WHERE usuario_id = :id AND modulo_codigo = :module";
        return jdbc.sql(sql).param("id", id).param("module", module.name()).update() != 0;
    }

    public List<ModuleOption> listModules() {
        return jdbc.sql("SELECT codigo, nombre FROM modulo ORDER BY codigo")
                .query((rs, row) -> new ModuleOption(Module.valueOf(rs.getString(1)), rs.getString(2))).list();
    }

    public void auditModule(long actor, long target, Module module, boolean grant) {
        jdbc.sql("""
                INSERT INTO auditoria_usuario_modulo (actor_usuario_id, destinatario_usuario_id, modulo_codigo, accion, origen)
                VALUES (:actor, :target, :module, :action, 'USUARIO')
                """).param("actor", actor).param("target", target).param("module", module.name())
                .param("action", grant ? "ASIGNADO" : "RETIRADO").update();
    }

    private static IdentityUser mapUser(ResultSet rs, int row) throws SQLException {
        var modules = Arrays.stream((String[]) rs.getArray("modulos").getArray()).map(Module::valueOf).toList();
        return new IdentityUser(rs.getLong("id"), rs.getString("nombre"), rs.getString("identificador_acceso"),
                Role.valueOf(rs.getString("rol")), rs.getBoolean("activo"),
                rs.getObject("creado_en", OffsetDateTime.class).toInstant(),
                rs.getObject("actualizado_en", OffsetDateTime.class).toInstant(), modules);
    }

    public record Credentials(long id, String hash, boolean active) {
        @Override
        public String toString() {
            return "Credentials[id=" + id + ", active=" + active + "]";
        }
    }

    public record ModuleOption(Module code, String name) {
    }
}
