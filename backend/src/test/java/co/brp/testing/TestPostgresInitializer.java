package co.brp.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Pattern;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.internal.DataSourceConnectionProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

public final class TestPostgresInitializer implements ApplicationContextInitializer<GenericApplicationContext> {

    // Una URL de un solo servidor, sin parámetros que puedan cambiar el destino o las credenciales.
    private static final Pattern TEST_URL = Pattern.compile(
            "jdbc:postgresql://(?:[a-zA-Z0-9.-]+|\\[[0-9a-fA-F:]+\\])(?::[0-9]{1,5})?/(brp_test_[a-z0-9_]+)");
    private static final Pattern TEST_USER = Pattern.compile("brp_test_[a-z0-9_]+");

    @Override
    public void initialize(GenericApplicationContext context) {
        var environment = context.getEnvironment();
        if (environment.matchesProfiles("dev")) {
            throw new IllegalStateException("El perfil dev no puede estar activo en pruebas de integración BRP.");
        }
        if (environment.containsProperty("spring.datasource.jndi-name")
                || environment.containsProperty("spring.datasource.hikari.data-source-jndi")) {
            throw new IllegalStateException("JNDI está prohibido en pruebas de integración BRP.");
        }

        var url = requiredVariable("BRP_TEST_JDBC_URL");
        var matcher = TEST_URL.matcher(url);
        if (!matcher.matches()) {
            throw new IllegalStateException("BRP_TEST_JDBC_URL debe apuntar a una base PostgreSQL brp_test_* sin parámetros.");
        }

        var database = matcher.group(1);
        // Se consulta .env únicamente como lista de exclusión, nunca como fuente de conexión.
        if (database.equals(System.getenv("POSTGRES_DB")) || database.equals(developmentDatabaseFromDotEnv())) {
            throw new IllegalStateException("La base de pruebas coincide con la base configurada para dev.");
        }

        var user = requiredVariable("BRP_TEST_DB_USER");
        if (!TEST_USER.matcher(user).matches()) {
            throw new IllegalStateException("BRP_TEST_DB_USER debe ser un rol exclusivo brp_test_*.");
        }
        var password = requiredVariable("BRP_TEST_DB_PASSWORD");
        verifyIsolatedRole(url, database, user, password);

        context.registerBean("dataSource", HikariDataSource.class, () -> {
            var dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(url);
            dataSource.setUsername(user);
            dataSource.setPassword(password);
            return dataSource;
        }, definition -> definition.setDestroyMethodName("close"));
        context.registerBean("liquibase", SpringLiquibase.class, () -> {
            var liquibase = new SpringLiquibase();
            liquibase.setDataSource(context.getBean("dataSource", HikariDataSource.class));
            liquibase.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
            return liquibase;
        });
        context.registerBean(HibernatePropertiesCustomizer.class, () -> properties -> {
            var dataSource = context.getBean("dataSource", HikariDataSource.class);
            properties.put(AvailableSettings.CONNECTION_PROVIDER, DataSourceConnectionProvider.class);
            properties.put(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource);
            properties.put(AvailableSettings.DATASOURCE, dataSource);
            properties.remove(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER);
        });
    }

    private static String requiredVariable(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta " + name + " para las pruebas de integración BRP.");
        }
        return value;
    }

    private static String developmentDatabaseFromDotEnv() {
        var file = Path.of("..", ".env");
        if (Files.notExists(file)) {
            return null;
        }
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            return properties.getProperty("POSTGRES_DB");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo verificar el nombre de la base dev.", e);
        }
    }

    private static void verifyIsolatedRole(String url, String database, String user, String password) {
        var sql = """
                select current_database() as database_name, role.rolname as user_name,
                       db.datdba = role.oid as owns_database,
                       (role.rolsuper or role.rolcreatedb or role.rolcreaterole
                           or role.rolreplication or role.rolbypassrls) as privileged,
                       exists (select 1 from pg_auth_members where member = role.oid) as has_memberships,
                       exists (
                           select 1 from pg_database other
                           where other.oid <> db.oid
                             and (other.datdba = role.oid or has_database_privilege(
                                 role.oid, other.oid, 'CONNECT, CREATE, TEMPORARY'))
                       ) as has_other_database_access
                from pg_database db
                join pg_roles role on role.rolname = current_user
                where db.datname = current_database()
                """;
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            if (!result.next() || !database.equals(result.getString("database_name"))
                    || !user.equals(result.getString("user_name")) || !result.getBoolean("owns_database")) {
                throw new IllegalStateException("La base PostgreSQL debe coincidir con la URL y pertenecer al rol de pruebas.");
            }
            if (result.getBoolean("privileged") || result.getBoolean("has_memberships")
                    || result.getBoolean("has_other_database_access")) {
                throw new IllegalStateException("El rol de pruebas tiene privilegios, membresías o acceso fuera de su base.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo validar la conexión PostgreSQL de pruebas (SQLSTATE "
                    + e.getSQLState() + ").");
        }
    }
}
