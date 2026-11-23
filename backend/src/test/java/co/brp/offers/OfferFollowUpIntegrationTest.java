package co.brp.offers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OfferFollowUpIntegrationTest {

    private static final String PATH = "/api/follow-up/offers";
    private static final String BRP = "BRP-0001";
    private static final List<String> FIELDS = List.of("registrationNumber", "brpCode", "total", "discount",
            "status", "analyst", "createdAt", "modifiedAt", "escalatedAt", "returnedAt", "closedAt");

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtEncoder encoder;
    @Autowired private JsonMapper json;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> users = new ArrayList<>();
    private final List<String> catalogFixtures = new ArrayList<>();

    @AfterEach
    void removeFixtures() {
        for (long id : users) {
            jdbc.update("""
                    DELETE FROM oferta_pago WHERE participacion_id IN (
                        SELECT p.id FROM oferta_participacion p JOIN oferta o ON o.id = p.oferta_id
                        WHERE o.radicador_id = ?
                    )
                    """, id);
            jdbc.update("DELETE FROM oferta_participacion WHERE oferta_id IN (SELECT id FROM oferta WHERE radicador_id = ?)", id);
            jdbc.update("DELETE FROM oferta_adjunto WHERE oferta_id IN (SELECT id FROM oferta WHERE radicador_id = ?)", id);
            jdbc.update("DELETE FROM oferta WHERE radicador_id = ?", id);
        }
        for (String code : catalogFixtures) {
            jdbc.update("DELETE FROM brp_catalog WHERE code = ?", code);
        }
        for (long id : users) {
            jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM usuario WHERE id = ?", id);
        }
    }

    @Test
    void registrantSeesOnlyOwnOffersAndCannotDistinguishForeignFromMissing() throws Exception {
        long owner = user("RADICADOR", "SEGUIMIENTO");
        long other = user("RADICADOR");
        long first = offer(owner, BRP, "RADICADO", null);
        long foreign = offer(other, BRP, "RADICADO", null);
        long latest = offer(owner, BRP, "RADICADO", null);
        var jwt = token(owner);

        assertThat(listNumbers(read(PATH, jwt))).containsExactly(latest, first);
        assertThat(listNumbers(read(PATH + "?registrantId=" + other, jwt))).containsExactly(latest, first);
        var own = read(PATH + "/" + first, jwt);
        assertThat(own.path("registrationNumber").asLong()).isEqualTo(first);
        assertThat(own.path("analyst").isNull()).isTrue();
        assertThat(own.path("createdAt").asString()).isEqualTo("2026-11-23T13:00:00Z");
        for (String field : List.of("modifiedAt", "escalatedAt", "returnedAt", "closedAt")) {
            assertThat(own.path(field).isNull()).as(field).isTrue();
        }

        var forbidden = get(PATH + "/" + foreign, jwt);
        var missing = get(PATH + "/" + Long.MAX_VALUE, jwt);
        assertThat(forbidden.statusCode()).isEqualTo(404);
        assertThat(missing.statusCode()).isEqualTo(404);
        var forbiddenProblem = json.readTree(forbidden.body()).deepCopy();
        var missingProblem = json.readTree(missing.body()).deepCopy();
        // ProblemDetail incluye en instance la ruta solicitada.
        ((tools.jackson.databind.node.ObjectNode) forbiddenProblem).remove("instance");
        ((tools.jackson.databind.node.ObjectNode) missingProblem).remove("instance");
        assertThat(forbiddenProblem).isEqualTo(missingProblem);
    }

    @Test
    void analystCanSeeOffersAssignedToDifferentAnalysts() throws Exception {
        long owner = user("RADICADOR");
        long actor = user("ANALISTA", "SEGUIMIENTO");
        long other = user("ANALISTA");
        long assigned = offer(owner, BRP, "EN_PROCESO", actor);
        long assignedElsewhere = offer(owner, BRP, "EN_PROCESO", other);
        var jwt = token(actor);

        assertThat(listNumbers(read(PATH, jwt))).contains(assigned, assignedElsewhere);
        var details = read(PATH + "/" + assignedElsewhere, jwt);
        assertThat(details.path("analyst").path("id").asLong()).isEqualTo(other);
        assertThat(details.path("analyst").path("name").asString()).isEqualTo("Usuario de seguimiento");
    }

    @ParameterizedTest
    @ValueSource(strings = {"JEFE", "ADMINISTRADOR"})
    void globalRolesWithModuleCanSeeDifferentOfferStates(String role) throws Exception {
        long first = user("RADICADOR");
        long second = user("RADICADOR");
        long analyst = user("ANALISTA");
        long registered = offer(first, BRP, "RADICADO", null);
        long approved = offer(second, BRP, "APROBADO", analyst);
        var jwt = token(user(role, "SEGUIMIENTO"));

        assertThat(listNumbers(read(PATH, jwt))).contains(registered, approved);
        assertThat(read(PATH + "/" + registered, jwt).path("status").asString()).isEqualTo("RADICADO");
        var closed = read(PATH + "/" + approved, jwt);
        assertThat(closed.path("status").asString()).isEqualTo("APROBADO");
        assertThat(closed.path("modifiedAt").asString()).isEqualTo("2026-11-23T17:00:00Z");
        assertThat(closed.path("escalatedAt").asString()).isEqualTo("2026-11-23T15:00:00Z");
        assertThat(closed.path("returnedAt").asString()).isEqualTo("2026-11-23T16:00:00Z");
        assertThat(closed.path("closedAt").asString()).isEqualTo("2026-11-23T17:00:00Z");
    }

    @Test
    void administrationModuleAloneDoesNotAllowFollowUp() throws Exception {
        long number = offer(user("RADICADOR"), BRP, "RADICADO", null);
        var jwt = token(user("ADMINISTRADOR", "ADMINISTRACION"));
        assertThat(get(PATH, jwt).statusCode()).isEqualTo(403);
        assertThat(get(PATH + "/" + number, jwt).statusCode()).isEqualTo(403);
    }

    @Test
    void rejectsUnauthenticatedAndInactiveUsers() throws Exception {
        long actor = user("RADICADOR", "SEGUIMIENTO");
        long number = offer(actor, BRP, "RADICADO", null);
        var jwt = token(actor);
        jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", actor);
        for (String route : List.of(PATH, PATH + "/" + number)) {
            assertThat(get(route, null).statusCode()).isEqualTo(401);
            assertThat(get(route, jwt).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void revocationAppliesToListAndDetailWithTheSameJwt() throws Exception {
        long actor = user("RADICADOR", "SEGUIMIENTO", "RADICACION");
        long number = offer(actor, BRP, "RADICADO", null);
        var jwt = token(actor);
        assertThat(listNumbers(read(PATH, jwt))).containsExactly(number);
        assertThat(get(PATH + "/" + number, jwt).statusCode()).isEqualTo(200);

        jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ? AND modulo_codigo = 'SEGUIMIENTO'", actor);
        assertThat(get(PATH, jwt).statusCode()).isEqualTo(403);
        assertThat(get(PATH + "/" + number, jwt).statusCode()).isEqualTo(403);
        assertThat(get("/api/offers/mine/" + number, jwt).statusCode()).isEqualTo(200);
    }

    @Test
    void keepsHistoricalDiscountAndNeverProjectsPrivateOfferData() throws Exception {
        long actor = user("RADICADOR", "SEGUIMIENTO");
        String code = "FOLLOW-UP-" + UUID.randomUUID();
        catalogFixtures.add(code);
        ingest(code, "2027-01-01", 320_000_000);
        long number = offer(actor, code, "RADICADO", null);
        insertPrivateData(number);
        ingest(code, "2027-02-01", 400_000_000);

        var jwt = token(actor);
        var listing = read(PATH, jwt);
        assertThat(listing.size()).isEqualTo(1);
        var detail = read(PATH + "/" + number, jwt);
        assertThat(listing.get(0)).isEqualTo(detail);
        assertThat(detail.propertyNames()).containsExactlyInAnyOrderElementsOf(FIELDS);
        assertThat(detail.path("brpCode").asString()).isEqualTo(code);
        assertThat(detail.path("total").decimalValue()).isEqualByComparingTo("160000000");
        assertThat(detail.path("discount").decimalValue()).isEqualByComparingTo("50.0000");
        assertThat(detail.toString()).doesNotContain("privado@example.test", "Entidad reservada", "privado.pdf", "oferente reservado");
    }

    private long user(String role, String... modules) {
        long id = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, identificador_acceso, password_hash, rol)
                SELECT 'Usuario de seguimiento', ?, password_hash, ? FROM usuario
                WHERE identificador_acceso = 'admin' RETURNING id
                """, Long.class, "follow-up-test-" + UUID.randomUUID(), role);
        users.add(id);
        for (String module : modules) {
            jdbc.update("INSERT INTO usuario_modulo VALUES (?, ?)", id, module);
        }
        return id;
    }

    private long offer(long owner, String code, String status, Long analyst) {
        return jdbc.queryForObject("""
                INSERT INTO oferta (radicador_id, brp_version_id, precio_referencia, valor_total, descuento,
                    estado, creado_en, version, analista_id, modificado_en, escalado_en, devuelto_en, finalizado_en)
                SELECT ?, id, price, price / 2, 50, ?, TIMESTAMPTZ '2026-11-23 13:00:00+00', 0,
                    CAST(? AS bigint),
                    CASE WHEN ? = 'APROBADO' THEN TIMESTAMPTZ '2026-11-23 17:00:00+00' END,
                    CASE WHEN ? = 'APROBADO' THEN TIMESTAMPTZ '2026-11-23 15:00:00+00' END,
                    CASE WHEN ? = 'APROBADO' THEN TIMESTAMPTZ '2026-11-23 16:00:00+00' END,
                    CASE WHEN ? = 'APROBADO' THEN TIMESTAMPTZ '2026-11-23 17:00:00+00' END
                FROM brp_catalog WHERE code = ? ORDER BY ingestion_date DESC LIMIT 1
                RETURNING numero_radicacion
                """, Long.class, owner, status, analyst, status, status, status, status, code);
    }

    private void ingest(String code, String day, int price) {
        jdbc.update("""
                INSERT INTO brp_catalog (code, ingestion_date, status, asset_type, property_registration,
                    license_plate, department, municipality, description, price, price_assigned_on)
                SELECT ?, CAST(? AS date), 'DISPONIBLE', asset_type, property_registration, license_plate,
                    department, municipality, description, ?, price_assigned_on
                FROM brp_catalog WHERE code = 'BRP-0001' ORDER BY ingestion_date DESC LIMIT 1
                """, code, day, price);
    }

    private void insertPrivateData(long number) {
        long offerId = jdbc.queryForObject("SELECT id FROM oferta WHERE numero_radicacion = ?", Long.class, number);
        long bidder = jdbc.queryForObject("""
                INSERT INTO oferta_participacion (oferta_id, tipo_identificacion, numero_identificacion, nombre,
                    contacto, porcentaje) VALUES (?, 'CC', '123', 'oferente reservado', 'privado@example.test', 100)
                RETURNING id
                """, Long.class, offerId);
        jdbc.update("""
                INSERT INTO oferta_pago (participacion_id, modalidad, fecha, valor, financiadora)
                VALUES (?, 'PRESTAMO', DATE '2026-12-01', 160000000, 'Entidad reservada')
                """, bidder);
        jdbc.update("""
                INSERT INTO oferta_adjunto (id, oferta_id, nombre_archivo, tipo_contenido, tamano_bytes, cargado_en)
                VALUES (?, ?, 'privado.pdf', 'application/pdf', 20, now())
                """, UUID.randomUUID(), offerId);
    }

    private List<Long> listNumbers(JsonNode response) {
        var numbers = new ArrayList<Long>();
        for (var entry : response) {
            numbers.add(entry.path("registrationNumber").asLong());
        }
        return numbers;
    }

    private JsonNode read(String path, String jwt) throws Exception {
        var result = get(path, jwt);
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        return json.readTree(result.body());
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
