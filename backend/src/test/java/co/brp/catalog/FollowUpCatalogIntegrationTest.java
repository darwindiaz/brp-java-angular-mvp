package co.brp.catalog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import co.brp.catalog.application.FollowUpCatalog;
import co.brp.identity.application.IdentityAuthentication;
import co.brp.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FollowUpCatalogIntegrationTest {

    private static final String CATALOG = "/api/follow-up/catalog";
    private static final String FRESHNESS = CATALOG + "/freshness";
    private static final String LATEST = "2027-04-01";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtEncoder encoder;
    @Autowired private JsonMapper json;
    @Autowired private FollowUpCatalog catalog;
    @Autowired private PlatformTransactionManager transactions;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> users = new ArrayList<>();
    private final List<String> fixtures = new ArrayList<>();

    @AfterEach
    void removeFixtures() {
        for (String code : fixtures) {
            jdbc.update("DELETE FROM brp_catalog WHERE code = ?", code);
        }
        for (long user : users) {
            jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ?", user);
            jdbc.update("DELETE FROM usuario WHERE id = ?", user);
        }
    }

    @Test
    void registrantOnlySeesAvailableCurrentVersions() throws Exception {
        String hidden = ingestBlockedLatest();
        String visible = newCode();
        ingest(visible, "2027-02-01", "DISPONIBLE", 350_000_000);
        String jwt = token(user("RADICADOR", "SEGUIMIENTO"));
        var rows = read(CATALOG, jwt);

        assertThat(codes(rows)).contains(visible).doesNotContain(hidden, "BRP-0045").isSorted().doesNotHaveDuplicates();
        for (var row : rows) {
            assertThat(row.path("status").asString()).isEqualTo("DISPONIBLE");
        }
        assertThat(read(FRESHNESS, jwt).path("latestIngestionDate").asString()).isEqualTo(LATEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANALISTA", "JEFE", "ADMINISTRADOR"})
    void globalRolesSeeOnlyTheLatestVersionWithTheMinimalProjection(String role) throws Exception {
        String code = ingestBlockedLatest();
        String jwt = token(user(role, "SEGUIMIENTO"));

        var rows = read(CATALOG, jwt);
        assertThat(codes(rows)).contains(code, "BRP-0045").isSorted().doesNotHaveDuplicates();
        var item = byCode(rows, code);
        assertThat(item.propertyNames()).containsExactlyInAnyOrder(
                "code", "assetType", "description", "location", "status", "referencePrice", "ingestionDate");
        assertThat(item.path("status").asString()).isEqualTo("SANEAMIENTO");
        assertThat(item.path("assetType").asString()).isEqualTo("APARTAMENTO");
        assertThat(item.path("ingestionDate").asString()).isEqualTo(LATEST);
        assertThat(item.path("referencePrice").decimalValue()).isEqualByComparingTo("400000000");
        assertThat(item.path("description").asString()).isNotBlank();
        assertThat(item.path("location").propertyNames()).containsExactlyInAnyOrder("department", "municipality");
        assertThat(byCode(rows, "BRP-0045").path("referencePrice").isNull()).isTrue();

        var freshness = read(FRESHNESS, jwt);
        assertThat(freshness.propertyNames()).containsExactly("latestIngestionDate");
        assertThat(freshness.path("latestIngestionDate").asString()).isEqualTo(LATEST);
    }

    @Test
    void bothRoutesRequireAnActiveAccountAndTheCurrentModule() throws Exception {
        long administrator = user("ADMINISTRADOR", "ADMINISTRACION");
        long active = user("RADICADOR", "SEGUIMIENTO");
        long inactive = user("ANALISTA", "SEGUIMIENTO");
        var adminJwt = token(administrator);
        var activeJwt = token(active);
        var inactiveJwt = token(inactive);
        jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", inactive);
        for (String path : List.of(CATALOG, FRESHNESS)) {
            assertThat(get(path, null).statusCode()).isEqualTo(401);
            assertThat(get(path, adminJwt).statusCode()).isEqualTo(403);
            assertThat(get(path, inactiveJwt).statusCode()).isEqualTo(401);
            assertThat(get(path, activeJwt).statusCode()).isEqualTo(200);
        }

        jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ? AND modulo_codigo = 'SEGUIMIENTO'", active);
        for (String path : List.of(CATALOG, FRESHNESS)) {
            assertThat(get(path, activeJwt).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void emptyPostgresCatalogHasNoIngestionDateAndSerializesItAsNull() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            // PostgreSQL resuelve la tabla temporal primero dentro de esta conexión.
            // La tabla del catálogo de pruebas y sus filas permanecen intactas.
            jdbc.execute("CREATE TEMP TABLE brp_catalog (LIKE public.brp_catalog) ON COMMIT DROP");
            var response = catalog.freshness();
            assertThat(response.latestIngestionDate()).isNull();
            JsonNode body = json.valueToTree(response);
            assertThat(body.propertyNames()).containsExactly("latestIngestionDate");
            assertThat(body.path("latestIngestionDate").isNull()).isTrue();
        });
    }

    private String ingestBlockedLatest() {
        String code = newCode();
        ingest(code, "2027-01-01", "DISPONIBLE", 350_000_000);
        ingest(code, LATEST, "SANEAMIENTO", 400_000_000);
        return code;
    }

    private String newCode() {
        String code = "FOLLOW-UP-CATALOG-" + UUID.randomUUID();
        fixtures.add(code);
        return code;
    }

    private void ingest(String code, String day, String status, int price) {
        jdbc.update("""
                INSERT INTO brp_catalog (code, ingestion_date, status, asset_type, property_registration,
                    license_plate, department, municipality, description, price, price_assigned_on)
                SELECT ?, CAST(? AS date), ?, asset_type, property_registration, license_plate,
                    department, municipality, description, ?, price_assigned_on
                FROM brp_catalog WHERE code = 'BRP-0001' ORDER BY ingestion_date DESC LIMIT 1
                """, code, day, status, price);
    }

    private long user(String role, String module) {
        long id = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, identificador_acceso, password_hash, rol)
                SELECT 'Usuario de catálogo de pruebas', ?, password_hash, ? FROM usuario
                WHERE identificador_acceso = 'admin' RETURNING id
                """, Long.class, "follow-up-catalog-test-" + UUID.randomUUID(), role);
        users.add(id);
        jdbc.update("INSERT INTO usuario_modulo VALUES (?, ?)", id, module);
        return id;
    }

    private List<String> codes(JsonNode rows) {
        return StreamSupport.stream(rows.spliterator(), false).map(row -> row.path("code").asString()).toList();
    }

    private JsonNode byCode(JsonNode rows, String code) {
        return StreamSupport.stream(rows.spliterator(), false)
                .filter(row -> row.path("code").asString().equals(code)).findFirst().orElseThrow();
    }

    private JsonNode read(String path, String jwt) throws Exception {
        var response = get(path, jwt);
        assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
        return json.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String jwt) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token(long actor) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(Long.toString(actor)).issuer(IdentityAuthentication.ISSUER)
                .audience(List.of(IdentityAuthentication.AUDIENCE)).issuedAt(now)
                .expiresAt(now.plus(IdentityAuthentication.TOKEN_LIFETIME)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
