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
import org.junit.jupiter.params.provider.CsvSource;
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
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OfferApiIntegrationTest {

    private static final String OFFER = """
            {"brpCode":"BRP-0001","bidders":[{"identificationType":"CC","identificationNumber":"123",
              "name":"Oferente","contact":"correo@example.test","percentage":100.0000,
              "payments":[{"method":"CONTADO","date":"2026-12-01","amount":160000000.00}]}]}
            """;

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtEncoder encoder;
    @Autowired
    private JsonMapper json;

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
            jdbc.update("DELETE FROM oferta WHERE radicador_id = ?", id);
            jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM usuario WHERE id = ?", id);
        }
        for (String code : catalogFixtures) {
            jdbc.update("DELETE FROM brp_catalog WHERE code = ?", code);
        }
    }

    @Test
    void createsOfferAndPersistsCompleteAggregate() throws Exception {
        var user = user("RADICADOR", true);
        var response = request("POST", "/api/offers", token(user), OFFER);
        assertThat(response.statusCode()).isEqualTo(201);
        var body = json.readTree(response.body());
        var number = body.path("registrationNumber").asLong();
        assertThat(number).isPositive();
        assertThat(body.path("brpCode").asString()).isEqualTo("BRP-0001");
        assertThat(body.path("referencePrice").decimalValue()).isEqualByComparingTo("320000000");
        assertThat(body.path("total").decimalValue()).isEqualByComparingTo("160000000");
        assertThat(body.path("status").asString()).isEqualTo("RADICADO");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM oferta o JOIN oferta_participacion p ON o.id = p.oferta_id
                JOIN oferta_pago payment ON p.id = payment.participacion_id
                WHERE o.numero_radicacion = ? AND o.radicador_id = ?
                """, Integer.class, number, user)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"NO-EXISTE,404", "BRP-0045,409"})
    void rejectsMissingAndUnavailableBrp(String code, int status) throws Exception {
        var response = request("POST", "/api/offers", token(user("RADICADOR", true)),
                OFFER.replace("BRP-0001", code));
        assertThat(response.statusCode()).isEqualTo(status);
    }

    @Test
    void requiresAuthenticationForAllOfferRoutes() throws Exception {
        assertThat(request("POST", "/api/offers", null, OFFER).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/offers/discount-preview", null, "{}").statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/offers/mine", null, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/offers/mine/1", null, null).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @CsvSource({"ANALISTA,true", "RADICADOR,false"})
    void requiresRoleAndModule(String role, boolean module) throws Exception {
        var jwt = token(user(role, module));
        assertThat(request("POST", "/api/offers", jwt, OFFER).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/offers", jwt, "{}").statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/offers/mine", jwt, null).statusCode()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"referencePrice", "registrantId", "total", "discount", "status"})
    void rejectsClientSuppliedServerFields(String field) throws Exception {
        var user = user("RADICADOR", true);
        var response = request("POST", "/api/offers", token(user),
                "{\"" + field + "\":1," + OFFER.substring(1));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).path("detail").asString())
                .isEqualTo("La solicitud contiene datos inválidos.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oferta WHERE radicador_id = ?", Integer.class, user)).isZero();
    }

    @Test
    void validatesNestedFieldsWithoutEchoingRejectedValues() throws Exception {
        var response = request("POST", "/api/offers", token(user("RADICADOR", true)),
                OFFER.replace("CONTADO", "PRIVATE-FIXTURE"));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("bidders[0].payments[0].method").doesNotContain("PRIVATE-FIXTURE");
    }

    @Test
    void returnsBusinessValidationWithoutPersistingAnInvalidOffer() throws Exception {
        var user = user("RADICADOR", true);
        var response = request("POST", "/api/offers", token(user), OFFER.replace("100.0000", "99.0000"));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Las participaciones deben sumar 100.0000 %.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oferta WHERE radicador_id = ?", Integer.class, user)).isZero();
    }

    @Test
    void filtersByOwnerAndHidesForeignOfferAsMissing() throws Exception {
        var jwt = token(user("RADICADOR", true));
        var own = create(jwt);
        var other = create(token(user("RADICADOR", true)));

        var listing = request("GET", "/api/offers/mine", jwt, null);
        assertThat(listing.statusCode()).isEqualTo(200);
        var list = json.readTree(listing.body());
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("registrationNumber").asLong()).isEqualTo(own);

        var response = request("GET", "/api/offers/mine/" + own, jwt, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var detail = json.readTree(response.body());
        assertThat(detail.path("registrationNumber").asLong()).isEqualTo(own);
        assertThat(detail.path("brpCode").asString()).isEqualTo("BRP-0001");
        assertThat(detail.path("discount").decimalValue()).isEqualByComparingTo("50");
        assertThat(detail.path("overprice").asBoolean()).isFalse();
        assertThat(detail.path("createdAt").isMissingNode()).isFalse();
        var bidder = detail.path("bidders").get(0);
        assertThat(bidder.path("percentage").decimalValue()).isEqualByComparingTo("100");
        assertThat(bidder.path("payments").get(0).path("method").asString()).isEqualTo("CONTADO");

        var foreign = request("GET", "/api/offers/mine/" + other, jwt, null);
        var missing = request("GET", "/api/offers/mine/" + Long.MAX_VALUE, jwt, null);
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(json.readTree(foreign.body()).path("detail")).isEqualTo(json.readTree(missing.body()).path("detail"));
    }

    @Test
    void rechecksPermissionsWhenUsingTheSameToken() throws Exception {
        var user = user("RADICADOR", true);
        var jwt = token(user);
        var number = create(jwt);
        jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ? AND modulo_codigo = 'RADICACION'", user);
        assertThat(request("GET", "/api/offers/mine", jwt, null).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/offers/mine/" + number, jwt, null).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", user);
        assertThat(request("GET", "/api/offers/mine", jwt, null).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @CsvSource({"160000000.00,50.0000,false", "320000000,0.0000,false", "480000000,-50.0000,true"})
    void previewsUsingTheSameDiscountRuleWithoutPersisting(String total, String discount, boolean overprice) throws Exception {
        var user = user("RADICADOR", true);
        var response = preview("BRP-0001", total, token(user));
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.path("referencePrice").decimalValue()).isEqualByComparingTo("320000000");
        assertThat(body.path("total").decimalValue()).isEqualByComparingTo(total);
        assertThat(body.path("discount").decimalValue()).isEqualByComparingTo(discount);
        assertThat(body.path("overprice").asBoolean()).isEqualTo(overprice);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oferta WHERE radicador_id = ?", Integer.class, user)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"BRP-0045,409", "NO-EXISTE,404"})
    void rejectsPreviewWithoutUsableBrp(String code, int status) throws Exception {
        assertThat(preview(code, "100.00", token(user("RADICADOR", true))).statusCode()).isEqualTo(status);
    }

    @Test
    void registrationReadsTheCurrentVersionAgainAfterPreview() throws Exception {
        var code = "OFFER-API-" + UUID.randomUUID();
        catalogFixtures.add(code);
        insertBrpVersion(code, "2027-01-01", 320_000_000);
        var jwt = token(user("RADICADOR", true));
        var preview = preview(code, "160000000.00", jwt);
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(json.readTree(preview.body()).path("referencePrice").decimalValue()).isEqualByComparingTo("320000000");

        insertBrpVersion(code, "2027-02-01", 400_000_000);
        var response = request("POST", "/api/offers", jwt, OFFER.replace("BRP-0001", code));
        assertThat(response.statusCode()).isEqualTo(201);
        var body = json.readTree(response.body());
        assertThat(body.path("referencePrice").decimalValue()).isEqualByComparingTo("400000000");
        assertThat(body.path("discount").decimalValue()).isEqualByComparingTo("60");
    }

    private void insertBrpVersion(String code, String ingestionDate, int price) {
        jdbc.update("""
                INSERT INTO brp_catalog (code, ingestion_date, status, asset_type, property_registration,
                    license_plate, department, municipality, description, price, price_assigned_on)
                SELECT ?, CAST(? AS date), 'DISPONIBLE', asset_type, property_registration,
                    license_plate, department, municipality, description, ?, price_assigned_on
                FROM brp_catalog WHERE code = 'BRP-0001' ORDER BY ingestion_date DESC LIMIT 1
                """, code, ingestionDate, price);
    }

    private long create(String jwt) throws Exception {
        var response = request("POST", "/api/offers", jwt, OFFER);
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).path("registrationNumber").asLong();
    }

    private HttpResponse<String> preview(String code, String total, String jwt) throws Exception {
        return request("POST", "/api/offers/discount-preview", jwt,
                "{\"brpCode\":\"" + code + "\",\"total\":" + total + "}");
    }

    private long user(String role, boolean module) {
        var id = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, identificador_acceso, password_hash, rol)
                SELECT 'Radicador de pruebas', ?, password_hash, ? FROM usuario
                WHERE identificador_acceso = 'admin' RETURNING id
                """, Long.class, "offer-api-" + UUID.randomUUID(), role);
        users.add(id);
        if (module) {
            jdbc.update("INSERT INTO usuario_modulo (usuario_id, modulo_codigo) VALUES (?, 'RADICACION')", id);
        }
        return id;
    }

    private String token(long user) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(Long.toString(user)).issuer(IdentityAuthentication.ISSUER)
                .audience(List.of(IdentityAuthentication.AUDIENCE)).issuedAt(now)
                .expiresAt(now.plus(IdentityAuthentication.TOKEN_LIFETIME)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private HttpResponse<String> request(String method, String path, String jwt, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
