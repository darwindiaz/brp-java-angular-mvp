package co.brp.identity.infrastructure;

import java.sql.SQLException;
import java.util.regex.Pattern;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

public class BootstrapAdministrator implements CustomTaskChange {

    private static final Pattern BCRYPT = Pattern.compile(
            "[$]2[aby][$](0[4-9]|[12][0-9]|3[01])[$][./A-Za-z0-9]{53}");

    @Override
    public void execute(Database database) throws CustomChangeException {
        var hash = System.getenv("BRP_BOOTSTRAP_ADMIN_HASH");
        if (hash == null || !BCRYPT.matcher(hash).matches()) {
            throw new CustomChangeException("Se requiere un hash BCrypt válido en BRP_BOOTSTRAP_ADMIN_HASH.");
        }
        var connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
        try (var statement = connection.prepareStatement("""
                WITH administrador AS (
                    INSERT INTO usuario (nombre, identificador_acceso, password_hash, rol)
                    VALUES ('Administrador inicial', 'admin', ?, 'ADMINISTRADOR') RETURNING id
                ), permiso AS (
                    INSERT INTO usuario_modulo (usuario_id, modulo_codigo)
                    SELECT id, 'ADMINISTRACION' FROM administrador RETURNING usuario_id, modulo_codigo
                )
                INSERT INTO auditoria_usuario_modulo (destinatario_usuario_id, modulo_codigo, accion, origen)
                SELECT usuario_id, modulo_codigo, 'ASIGNADO', 'SISTEMA' FROM permiso
                """)) {
            statement.setString(1, hash);
            statement.executeUpdate();
        } catch (SQLException exception) {
            // El detalle del servidor puede incluir el valor enlazado.
            throw new CustomChangeException("Falló el aprovisionamiento inicial (SQLSTATE "
                    + exception.getSQLState() + ").");
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Administrador inicial aprovisionado.";
    }

    @Override
    public void setUp() {
    }

    @Override
    public void setFileOpener(ResourceAccessor accessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
