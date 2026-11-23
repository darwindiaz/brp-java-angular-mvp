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

import co.brp.catalog.application.BrpCatalog;
import co.brp.identity.application.IdentityAuthentication;
import co.brp.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@PostgresIntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationCatalogIntegrationTest {

    private static final String SELECTION = "/api/offers/catalog";
    private static final String FOLLOW_UP = "/api/follow-up/catalog";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtEncoder encoder;
    @Autowired private JsonMapper json;
    @MockitoSpyBean private BrpCatalog catalog;

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
    void registrantWithoutFollowUpSeesLatestAvailableVersionsAndOnlySelectionFields() throws Exception {
        String blocked = newCode();
        String available = newCode();
        ingest(blocked, "2027-01-01", "DISPONIBLE", 350_000_000);
        ingest(blocked, "2027-02-01", "SANEAMIENTO", 390_000_000);
        ingest(available, "2027-01-01", "DISPONIBLE", 360_000_000);
        ingest(available, "2027-02-01", "DISPONIBLE", 400_000_000);
        String jwt = token(user("RADICADOR", "RADICACION"));

        var response = get(SELECTION, jwt);
        assertThat(response.statusCode()).isEqualTo(200);
        var items = json.readTree(response.body());
        var codes = StreamSupport.stream(items.spliterator(), false).map(item -> item.path("code").asString()).toList();
        assertThat(codes).contains(available).doesNotContain(blocked, "BRP-0045").doesNotHaveDuplicates().isSorted();
        var selected = StreamSupport.stream(items.spliterator(), false)
                .filter(item -> item.path("code").asString().equals(available)).findFirst().orElseThrow();
        assertThat(selected.propertyNames()).containsExactlyInAnyOrder(
                "code", "assetType", "description", "location", "referencePrice");
        assertThat(selected.path("referencePrice").decimalValue()).isEqualByComparingTo("400000000");
        assertThat(selected.path("location").propertyNames()).containsExactlyInAnyOrder("department", "municipality");

        for (String path : List.of("/api/follow-up/offers", "/api/follow-up/offers/1", FOLLOW_UP, FOLLOW_UP + "/freshness")) {
            assertThat(get(path, jwt).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void followUpDoesNotGrantRegistrationAndAdministratorHasNoImplicitAccess() throws Exception {
        var followUpToken = token(user("RADICADOR", "SEGUIMIENTO"));
        assertThat(get(SELECTION, followUpToken).statusCode()).isEqualTo(403);
        assertThat(get(FOLLOW_UP, followUpToken).statusCode()).isEqualTo(200);
        assertThat(get(FOLLOW_UP + "/freshness", followUpToken).statusCode()).isEqualTo(200);
        assertThat(get("/api/follow-up/offers", followUpToken).statusCode()).isEqualTo(200);
        assertThat(get("/api/follow-up/offers/" + Long.MAX_VALUE, followUpToken).statusCode()).isEqualTo(404);

        var adminToken = token(user("ADMINISTRADOR", "ADMINISTRACION", "RADICACION"));
        assertThat(get(SELECTION, adminToken).statusCode()).isEqualTo(403);
    }

    @Test
    void rejectsUnauthenticatedAndInactiveAccounts() throws Exception {
        var id = user("RADICADOR", "RADICACION");
        var jwt = token(id);
        jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", id);

        assertThat(get(SELECTION, null).statusCode()).isEqualTo(401);
        assertThat(get(SELECTION, jwt).statusCode()).isEqualTo(401);
    }

    @Test
    void revocationAppliesToTheSameJwtWithoutChangingFollowUpPermissions() throws Exception {
        var id = user("RADICADOR", "RADICACION", "SEGUIMIENTO");
        var jwt = token(id);
        assertThat(get(SELECTION, jwt).statusCode()).isEqualTo(200);

        jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ? AND modulo_codigo = 'RADICACION'", id);
        assertThat(get(SELECTION, jwt).statusCode()).isEqualTo(403);
        assertThat(get(FOLLOW_UP, jwt).statusCode()).isEqualTo(200);
    }

    @Test
    void returnsAnEmptyArrayWhenNoAvailableVersionExists() throws Exception {
        doReturn(List.of()).when(catalog).available();
        var response = get(SELECTION, token(user("RADICADOR", "RADICACION")));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[]");
    }

    private String newCode() {
        var code = "REGISTRATION-CATALOG-" + UUID.randomUUID();
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

    private long user(String role, String... modules) {
        long id = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, identificador_acceso, password_hash, rol)
                SELECT 'Usuario de catálogo de radicación', ?, password_hash, ? FROM usuario
                WHERE identificador_acceso = 'admin' RETURNING id
                """, Long.class, "registration-catalog-test-" + UUID.randomUUID(), role);
        users.add(id);
        for (String module : modules) {
            jdbc.update("INSERT INTO usuario_modulo VALUES (?, ?)", id, module);
        }
        return id;
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
