package co.brp.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import co.brp.identity.application.IdentityAdministration;
import co.brp.identity.application.IdentityAuthentication;
import co.brp.testing.PostgresIntegrationTest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"brp.identity.login.max-attempts=50",
                "logging.level.org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor=DEBUG"})
class IdentityIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private SecretKey jwtSecret;
    @Autowired
    private JwtDecoder decoder;
    @Autowired
    private IdentityAdministration administration;

    private final String fixturePrefix = "identity-test-" + UUID.randomUUID();
    private final String fixturePassword = UUID.randomUUID().toString();

    @AfterEach
    void removeTestFixturesFromIsolatedDatabase() {
        jdbc.update("UPDATE usuario SET activo = true WHERE identificador_acceso = 'admin' AND NOT activo");
        var target = "SELECT id FROM usuario WHERE identificador_acceso LIKE ?";
        jdbc.update("DELETE FROM auditoria_usuario_modulo WHERE destinatario_usuario_id IN (" + target
                + ") OR actor_usuario_id IN (" + target + ")", fixturePrefix + "%", fixturePrefix + "%");
        jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id IN (" + target + ")", fixturePrefix + "%");
        jdbc.update("DELETE FROM usuario WHERE identificador_acceso LIKE ?", fixturePrefix + "%");
    }

    @Test
    void migratesAndBootstrapsEffectiveAdministratorWithBcrypt() {
        assertThat(jdbc.queryForList("SELECT id FROM databasechangelog ORDER BY orderexecuted", String.class))
                .contains("001-create-brp-catalog", "002-load-brp-catalog", "003-reject-blank-brp-identifiers",
                        "004-create-identity", "005-bootstrap-administrator");
        var hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE identificador_acceso = 'admin'", String.class);
        assertThat(passwords.matches(adminPassword(), hash)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auditoria_usuario_modulo
                WHERE actor_usuario_id IS NULL AND destinatario_usuario_id = ?
                AND origen = 'SISTEMA' AND accion = 'ASIGNADO' AND modulo_codigo = 'ADMINISTRACION'
                """, Integer.class, adminId())).isEqualTo(1);
    }

    @Test
    void loginReturnsFifteenMinuteTokenAndMeNeverReturnsHash() throws Exception {
        var token = loginAs(" ADMIN ", adminPassword());
        var jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(adminId()));
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
        var response = request("GET", "/api/auth/me", token, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("ADMINISTRADOR", "ADMINISTRACION").doesNotContain("hash", "password");
    }

    @Test
    void loginFailuresAreGenericAndInactiveTokenReturns401() throws Exception {
        var admin = adminToken();
        var user = create(admin, "RADICADOR");
        var token = loginAs(user.login(), fixturePassword);
        var wrong = login(user.login(), UUID.randomUUID().toString());
        var missing = login(fixturePrefix + "-missing", fixturePassword);
        expect(204, "PATCH", user.path() + "/status", admin, Map.of("active", false));
        var inactive = login(user.login(), fixturePassword);
        assertThat(List.of(wrong.statusCode(), missing.statusCode(), inactive.statusCode())).containsOnly(401);
        assertThat(json.readTree(wrong.body()).get("detail").asText())
                .isEqualTo(json.readTree(missing.body()).get("detail").asText())
                .isEqualTo(json.readTree(inactive.body()).get("detail").asText());
        expect(401, "GET", "/api/auth/me", token, null);
        expect(204, "PATCH", user.path() + "/status", admin, Map.of("active", true));
        assertThat(login(user.login(), fixturePassword).statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsExpiredAlteredAndWrongIssuerOrAudienceTokens() throws Exception {
        var now = Instant.now();
        var alive = now.plusSeconds(900);
        var valid = signed(IdentityAuthentication.ISSUER, IdentityAuthentication.AUDIENCE, now, alive);
        expect(200, "GET", "/api/auth/me", valid, null);
        var signatureStart = valid.lastIndexOf('.') + 1;
        var altered = valid.substring(0, signatureStart)
                + (valid.charAt(signatureStart) == 'A' ? 'B' : 'A') + valid.substring(signatureStart + 1);
        for (var invalid : List.of(altered,
                signed(IdentityAuthentication.ISSUER, IdentityAuthentication.AUDIENCE, now.minusSeconds(60), now.minusSeconds(5)),
                signed("otro-emisor", IdentityAuthentication.AUDIENCE, now, alive),
                signed(IdentityAuthentication.ISSUER, "otra-api", now, alive))) {
            expect(401, "GET", "/api/auth/me", invalid, null);
        }
    }

    @Test
    void rejectsSignedTokensOutsideTheTemporalPolicy() throws Exception {
        var now = Instant.now();
        for (var invalid : List.of(
                signed(IdentityAuthentication.ISSUER, IdentityAuthentication.AUDIENCE, now.plusSeconds(60), now.plusSeconds(120)),
                signed(IdentityAuthentication.ISSUER, IdentityAuthentication.AUDIENCE, now, null),
                signed(IdentityAuthentication.ISSUER, IdentityAuthentication.AUDIENCE, null, now.plusSeconds(60)),
                signed(IdentityAuthentication.ISSUER, IdentityAuthentication.AUDIENCE, now, now),
                signed(IdentityAuthentication.ISSUER, IdentityAuthentication.AUDIENCE, now.plusSeconds(2), now.plusSeconds(1)),
                signed(IdentityAuthentication.ISSUER, IdentityAuthentication.AUDIENCE, now, now.plusSeconds(901)))) {
            expect(401, "GET", "/api/auth/me", invalid, null);
        }
    }

    @Test
    void publicEndpointsIgnoreBearerWhileProtectedEndpointsRequireValidToken() throws Exception {
        expect(200, "GET", "/actuator/health", null, null);
        expect(200, "GET", "/actuator/health", "invalid-bearer", null);
        var payload = Map.of("login", "admin", "password", adminPassword());
        expect(200, "POST", "/api/auth/login", null, payload);
        var response = request("POST", "/api/auth/login", "invalid-bearer", payload);
        assertThat(response.statusCode()).isEqualTo(200);
        expect(401, "GET", "/api/auth/me", null, null);
        expect(401, "GET", "/api/auth/me", "invalid-bearer", null);
        expect(401, "GET", "/api/admin/users", null, null);
        expect(200, "GET", "/api/auth/me", json.readTree(response.body()).get("accessToken").asText(), null);
    }

    @Test
    void rejectsInvalidPasswordsWithoutLeakingValues(CapturedOutput output) throws Exception {
        var invalidPassword = "invalid-password-" + UUID.randomUUID() + UUID.randomUUID();
        var loginError = login("admin", invalidPassword);
        var token = adminToken();
        var createError = request("POST", "/api/admin/users", token,
                Map.of("name", "Cuenta de prueba", "login", fixturePrefix,
                        "password", invalidPassword, "role", "RADICADOR"));
        var unreadable = request("POST", "/api/auth/login", null,
                Map.of("login", "admin", "password", Map.of("value", invalidPassword)));
        for (var response : List.of(loginError, createError, unreadable)) {
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain(invalidPassword);
            assertThat(json.readTree(response.body()).get("errors").get(0).get("field").asText())
                    .isEqualTo("password");
        }
        expect(400, "GET", "/api/admin/users/not-an-id", token, null);
        assertThat(output.getAll()).doesNotContain(invalidPassword);
        assertThat(output.getAll()).doesNotContain(adminPassword(), token);
    }

    @Test
    void enforcesCurrentRoleAndModuleAndAuditsOnlyEffectiveChanges() throws Exception {
        var admin = adminToken();
        var user = create(admin, "RADICADOR");
        var token = loginAs(user.login(), fixturePassword);
        var grantPath = user.path() + "/modules/ADMINISTRACION";
        expect(200, "GET", "/api/admin/users", admin, null);
        expect(200, "GET", user.path(), admin, null);
        expect(200, "GET", "/api/admin/modules", admin, null);
        expect(403, "GET", "/api/admin/users", token, null);
        expect(204, "PUT", grantPath, admin, null);
        expect(204, "PUT", grantPath, admin, null);
        expect(403, "GET", "/api/admin/users", token, null);
        expect(204, "PATCH", user.path() + "/role", admin, Map.of("role", "ADMINISTRADOR"));
        expect(200, "GET", "/api/admin/users", token, null);
        assertThat(request("GET", user.path() + "/modules", admin, null).body()).contains("ADMINISTRACION");
        expect(204, "DELETE", grantPath, admin, null);
        expect(204, "DELETE", grantPath, admin, null);
        expect(403, "GET", "/api/admin/users", token, null);
        assertThat(jdbc.queryForList("""
                SELECT accion FROM auditoria_usuario_modulo
                WHERE actor_usuario_id = ? AND destinatario_usuario_id = ?
                AND modulo_codigo = 'ADMINISTRACION' AND origen = 'USUARIO' AND ocurrido_en IS NOT NULL
                ORDER BY id
                """, String.class, adminId(), user.id())).containsExactly("ASIGNADO", "RETIRADO");
    }

    @Test
    void protectsOwnAdministrativeAccessButAllowsOtherModules() throws Exception {
        var token = adminToken();
        var path = "/api/admin/users/" + adminId();
        expect(409, "DELETE", path + "/modules/ADMINISTRACION", token, null);
        expect(409, "PATCH", path + "/role", token, Map.of("role", "ANALISTA"));
        expect(409, "PATCH", path + "/status", token, Map.of("active", false));
        expect(204, "PUT", path + "/modules/ANALISIS", token, null);
        try {
            expect(204, "DELETE", path + "/modules/ANALISIS", token, null);
        } finally {
            jdbc.update("""
                    DELETE FROM auditoria_usuario_modulo
                    WHERE actor_usuario_id = ? AND destinatario_usuario_id = ? AND modulo_codigo = 'ANALISIS'
                    """, adminId(), adminId());
        }
        assertThat(effectiveAdministrators()).isEqualTo(1);
    }

    @Test
    void concurrentTransactionsCannotRemoveBothAdministrators() throws Exception {
        var first = adminId();
        var token = adminToken();
        var second = create(token, "ADMINISTRADOR");
        expect(204, "PUT", second.path() + "/modules/ADMINISTRACION", token, null);
        assertThat(effectiveAdministrators()).isEqualTo(2);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(deactivateAfterBarrier(barrier, first, second.id()));
            var b = executor.submit(deactivateAfterBarrier(barrier, second.id(), first));
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("confirmed", "rejected");
        }
        assertThat(effectiveAdministrators()).isEqualTo(1);
    }

    private Callable<String> deactivateAfterBarrier(CyclicBarrier barrier, long actor, long target) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                administration.changeStatus(actor, target, false);
                return "confirmed";
            } catch (AccessDeniedException | ResponseStatusException expected) {
                return "rejected";
            }
        };
    }

    private int effectiveAdministrators() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM usuario u JOIN usuario_modulo m ON m.usuario_id = u.id
                WHERE u.activo AND u.rol = 'ADMINISTRADOR' AND m.modulo_codigo = 'ADMINISTRACION'
                """, Integer.class);
    }

    private long adminId() {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE identificador_acceso = 'admin'", Long.class);
    }

    private String adminToken() throws Exception {
        return loginAs("admin", adminPassword());
    }

    private static String adminPassword() {
        var value = System.getenv("BRP_TEST_ADMIN_LOGIN_PASSWORD");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Configura BRP_TEST_ADMIN_LOGIN_PASSWORD para las pruebas de identidad.");
        }
        return value;
    }

    private FixtureUser create(String admin, String role) throws Exception {
        var login = fixturePrefix + "-user";
        var response = request("POST", "/api/admin/users", admin,
                Map.of("name", "Cuenta de prueba", "login", login.toUpperCase(java.util.Locale.ROOT),
                        "password", fixturePassword, "role", role));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).doesNotContain("password", "hash").contains(login);
        return new FixtureUser(json.readTree(response.body()).get("id").asLong(), login);
    }

    private String signed(String issuer, String audience, Instant issuedAt, Instant expiresAt) throws JOSEException {
        var claims = new JWTClaimsSet.Builder().subject(Long.toString(adminId())).issuer(issuer).audience(List.of(audience));
        if (issuedAt != null) {
            claims.issueTime(Date.from(issuedAt));
        }
        if (expiresAt != null) {
            claims.expirationTime(Date.from(expiresAt));
        }
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        token.sign(new MACSigner(jwtSecret));
        return token.serialize();
    }

    private String loginAs(String login, String password) throws Exception {
        var response = login(login, password);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("accessToken").asText();
    }

    private HttpResponse<String> login(String login, String password) throws Exception {
        return request("POST", "/api/auth/login", null, Map.of("login", login, "password", password));
    }

    private void expect(int status, String method, String path, String token, Object body) throws Exception {
        assertThat(request(method, path, token, body).statusCode()).as("%s %s", method, path).isEqualTo(status);
    }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(java.time.Duration.ofSeconds(15));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private record FixtureUser(long id, String login) {
        String path() {
            return "/api/admin/users/" + id;
        }
    }
}
