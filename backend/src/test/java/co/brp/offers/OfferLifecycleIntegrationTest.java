package co.brp.offers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import co.brp.identity.application.IdentityAuthentication;
import co.brp.offers.domain.Offer.Action;
import co.brp.offers.infrastructure.OfferRepository;
import co.brp.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.OptimisticLockingFailureException;
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
class OfferLifecycleIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtEncoder encoder;
    @Autowired private JsonMapper json;
    @Autowired private OfferRepository offers;
    @Autowired private PlatformTransactionManager transactions;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void removeFixtures() {
        for (var id : users) {
            jdbc.update("""
                    DELETE FROM oferta_pago WHERE participacion_id IN (
                        SELECT p.id FROM oferta_participacion p JOIN oferta o ON o.id = p.oferta_id
                        WHERE o.radicador_id = ?
                    )
                    """, id);
            jdbc.update("DELETE FROM oferta_participacion WHERE oferta_id IN (SELECT id FROM oferta WHERE radicador_id = ?)", id);
            jdbc.update("DELETE FROM oferta WHERE radicador_id = ?", id);
        }
        for (var id : users) {
            jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM usuario WHERE id = ?", id);
        }
    }

    @Test
    void returnsToOriginalAnalystAndPersistsTraceabilityThroughApproval() throws Exception {
        long number = registerByApi(user("RADICADOR", "RADICACION"));
        long analyst = user("ANALISTA", "ANALISIS");
        long another = user("ANALISTA", "ANALISIS");
        long chief = user("JEFE", "APROBACION");
        assertThat(queueContains("analysis", analyst, number)).isTrue();

        var taken = transition("analysis", "take", number, 0, analyst);
        assertThat(taken.path("analystId").asLong()).isEqualTo(analyst);
        assertThat(taken.path("version").asLong()).isEqualTo(1);
        assertThat(queueContains("analysis", another, number)).isFalse();
        assertThat(post("analysis", "escalate", number, 1, another).statusCode()).isEqualTo(403);
        var escalated = transition("analysis", "escalate", number, 1, analyst);
        assertThat(escalated.path("escalatedAt").asString()).isNotBlank();
        assertThat(queueContains("approval", chief, number)).isTrue();

        var returned = transition("approval", "return", number, 2, chief);
        assertThat(returned.path("offer").path("status").asString()).isEqualTo("EN_PROCESO");
        assertThat(returned.path("analystId").asLong()).isEqualTo(analyst);
        assertThat(returned.path("returnedAt").asString()).isNotBlank();
        assertThat(queueContains("approval", chief, number)).isFalse();
        assertThat(queueContains("analysis", analyst, number)).isTrue();
        assertThat(post("analysis", "escalate", number, 1, analyst).statusCode()).isEqualTo(409);
        transition("analysis", "escalate", number, 3, analyst);
        var approved = transition("approval", "approve", number, 4, chief);
        assertThat(approved.path("offer").path("status").asString()).isEqualTo("APROBADO");
        assertThat(approved.path("version").asLong()).isEqualTo(5);
        assertThat(post("approval", "reject", number, 5, chief).statusCode()).isEqualTo(409);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM oferta WHERE numero_radicacion = ? AND analista_id = ?
                AND estado = 'APROBADO' AND version = 5 AND escalado_en >= devuelto_en
                AND finalizado_en >= escalado_en AND modificado_en = finalizado_en
                """, Integer.class, number, analyst)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM oferta o JOIN oferta_participacion p ON o.id = p.oferta_id
                JOIN oferta_pago payment ON p.id = payment.participacion_id
                WHERE o.numero_radicacion = ? AND o.precio_referencia = 320000000
                """, Integer.class, number)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"analysis,1", "approval,2"})
    void rejectsAndClosesFromEitherAuthorizedStage(String queue, long version) throws Exception {
        long number = registered(user("RADICADOR", "RADICACION"));
        long analyst = user("ANALISTA", "ANALISIS");
        transition("analysis", "take", number, 0, analyst);
        long actor = analyst;
        if (version == 2) {
            transition("analysis", "escalate", number, 1, analyst);
            actor = user("JEFE", "APROBACION");
        }
        var rejected = transition(queue, "reject", number, version, actor);
        assertThat(rejected.path("offer").path("status").asString()).isEqualTo("RECHAZADO");
        assertThat(rejected.path("closedAt").asString()).isNotBlank();
    }

    @Test
    void enforcesPermissionsSeparationAndHttpErrors() throws Exception {
        long registrant = user("RADICADOR", "RADICACION");
        long number = registered(registrant);
        long analyst = user("ANALISTA", "ANALISIS");
        long chief = user("JEFE", "APROBACION");
        assertThat(post("analysis", "take", number, 0, null).statusCode()).isEqualTo(401);
        assertThat(post("analysis", "take", number, 0, chief).statusCode()).isEqualTo(403);
        assertThat(post("analysis", "take", number, 0, user("ANALISTA", null)).statusCode()).isEqualTo(403);
        assertThat(post("analysis", "take", Long.MAX_VALUE, 0, analyst).statusCode()).isEqualTo(404);
        assertThat(post("analysis", "take", number, -1, analyst).statusCode()).isEqualTo(400);
        assertThat(post("analysis", "escalate", number, 0, analyst).statusCode()).isEqualTo(409);
        assertThat(post("approval", "approve", number, 0, chief).statusCode()).isEqualTo(409);

        changeRole(registrant, "ANALISTA", "ANALISIS");
        assertThat(post("analysis", "take", number, 0, registrant).statusCode()).isEqualTo(403);
        transition("analysis", "take", number, 0, analyst);
        transition("analysis", "escalate", number, 1, analyst);
        assertThat(post("approval", "approve", number, 2, analyst).statusCode()).isEqualTo(403);
        assertThat(post("approval", "approve", number, 2, user("JEFE", null)).statusCode()).isEqualTo(403);

        changeRole(analyst, "JEFE", "APROBACION");
        assertThat(post("approval", "approve", number, 2, analyst).statusCode()).isEqualTo(403);
        transition("approval", "approve", number, 2, chief);
    }

    @Test
    void httpCompetingTakesReturnOneSuccessAndOneConflict() throws Exception {
        long number = registered(user("RADICADOR", "RADICACION"));
        long first = user("ANALISTA", "ANALISIS");
        long second = user("ANALISTA", "ANALISIS");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> post("analysis", "take", number, 0, first).statusCode());
            var b = pool.submit(() -> post("analysis", "take", number, 0, second).statusCode());
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM oferta WHERE numero_radicacion = ?", Long.class, number))
                .isEqualTo(1L);
    }

    @Test
    void postgresRejectsAConcurrentWriteAfterBothTransactionsReadTheSameVersion() throws Exception {
        long number = registered(user("RADICADOR", "RADICACION"));
        long first = user("ANALISTA", "ANALISIS");
        long second = user("ANALISTA", "ANALISIS");
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> takeInTransaction(number, first, barrier));
            var b = pool.submit(() -> takeInTransaction(number, second, barrier));
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM oferta WHERE numero_radicacion = ? AND version = 1
                AND estado = 'EN_PROCESO' AND analista_id IN (?, ?) AND modificado_en IS NOT NULL
                """, Integer.class, number, first, second)).isEqualTo(1);
    }

    private boolean takeInTransaction(long number, long actor, CyclicBarrier barrier) {
        try {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                var offer = offers.findByRegistrationNumber(number).orElseThrow();
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } catch (java.util.concurrent.BrokenBarrierException | java.util.concurrent.TimeoutException exception) {
                    throw new IllegalStateException(exception);
                }
                offer.transition(Action.TAKE, actor, 0, Instant.now());
                offers.flush();
            });
            return true;
        } catch (OptimisticLockingFailureException exception) {
            return false;
        }
    }

    private long registered(long registrant) {
        return jdbc.queryForObject("""
                INSERT INTO oferta (radicador_id, brp_version_id, precio_referencia, valor_total,
                    descuento, estado, creado_en, version)
                SELECT ?, id, price, price, 0, 'RADICADO', CURRENT_TIMESTAMP, 0
                FROM brp_catalog WHERE code = 'BRP-0001' ORDER BY ingestion_date DESC LIMIT 1
                RETURNING numero_radicacion
                """, Long.class, registrant);
    }

    private long registerByApi(long registrant) throws Exception {
        var payload = """
                {"brpCode":"BRP-0001","bidders":[{"identificationType":"CC","identificationNumber":"123",
                  "name":"Oferente","percentage":100.0000,
                  "payments":[{"method":"CONTADO","date":"2026-12-01","amount":160000000.00}]}]}
                """;
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/offers"))
                .header("Authorization", "Bearer " + token(registrant))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).path("registrationNumber").asLong();
    }

    private long user(String role, String module) {
        long id = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, identificador_acceso, password_hash, rol)
                SELECT 'Usuario de pruebas', ?, password_hash, ? FROM usuario
                WHERE identificador_acceso = 'admin' RETURNING id
                """, Long.class, "lifecycle-test-" + UUID.randomUUID(), role);
        users.add(id);
        if (module != null) {
            jdbc.update("INSERT INTO usuario_modulo VALUES (?, ?)", id, module);
        }
        return id;
    }

    private void changeRole(long id, String role, String module) {
        jdbc.update("UPDATE usuario SET rol = ? WHERE id = ?", role, id);
        jdbc.update("INSERT INTO usuario_modulo VALUES (?, ?)", id, module);
    }

    private boolean queueContains(String queue, long actor, long number) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/offers/" + queue))
                .header("Authorization", "Bearer " + token(actor)).GET().build();
        var result = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(200);
        for (var item : json.readTree(result.body())) {
            if (item.path("offer").path("registrationNumber").asLong() == number) {
                return true;
            }
        }
        return false;
    }

    private JsonNode transition(String queue, String action, long number, long version, long actor) throws Exception {
        var result = post(queue, action, number, version, actor);
        assertThat(result.statusCode()).as("%s %s: %s", queue, action, result.body()).isEqualTo(200);
        return json.readTree(result.body());
    }

    private HttpResponse<String> post(String queue, String action, long number, long version, Long actor) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/offers/"
                        + queue + "/" + number + "/" + action))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"version\":" + version + "}"));
        if (actor != null) {
            request.header("Authorization", "Bearer " + token(actor));
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
