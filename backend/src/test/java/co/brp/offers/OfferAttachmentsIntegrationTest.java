package co.brp.offers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import co.brp.identity.application.IdentityAuthentication;
import co.brp.offers.infrastructure.LocalAttachmentStorage;
import co.brp.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@PostgresIntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class OfferAttachmentsIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @TempDir static Path directory;

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry registry) {
        registry.add("brp.attachments.directory", () -> directory.toString());
        registry.add("brp.attachments.max-file-size", () -> "1KB");
    }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtEncoder encoder;
    @Autowired private JsonMapper json;
    @MockitoSpyBean private LocalAttachmentStorage storage;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> users = new ArrayList<>();
    private final List<Long> numbers = new ArrayList<>();

    @AfterEach
    void removeFixtures() throws IOException {
        for (long number : numbers) {
            jdbc.update("DELETE FROM oferta_adjunto WHERE oferta_id IN (SELECT id FROM oferta WHERE numero_radicacion = ?)", number);
            jdbc.update("DELETE FROM oferta WHERE numero_radicacion = ?", number);
        }
        for (long user : users) {
            jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ?", user);
            jdbc.update("DELETE FROM usuario WHERE id = ?", user);
        }
        try (var entries = Files.list(directory)) {
            for (var path : entries.toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void uploadsListsAndDownloadsAllSupportedFormatsWithoutLeakingPaths() throws Exception {
        var owner = user("RADICADOR", "RADICACION");
        long number = offer(owner);
        var response = upload(number, owner, new Part("revisión.pdf", "application/pdf", PDF),
                new Part("a.jpg", "image/jpeg", JPG), new Part("b.jpeg", "image/jpeg", JPG),
                new Part("c.png", "image/png", PNG));
        assertThat(response.statusCode()).isEqualTo(201);
        var body = json.readTree(response.body());
        assertThat(body.size()).isEqualTo(4);
        var id = body.get(0).path("id").asString();
        assertThat(body.get(0).path("filename").asString()).isEqualTo("revisión.pdf");
        assertThat(body.get(0).path("size").asLong()).isEqualTo(PDF.length);
        assertThat(body.get(0).path("uploadedAt").asString()).isNotBlank();
        assertThat(asText(response)).doesNotContain(directory.toString(), "ruta", "path", "storage");
        assertThat(count(number)).isEqualTo(4);
        assertThat(diskFiles()).hasSize(4).contains(id);
        assertThat(diskFiles()).doesNotContain("revisión.pdf", "a.jpg");

        var list = get(route(number), owner);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json.readTree(list.body()).size()).isEqualTo(4);
        assertThat(asText(list)).doesNotContain(directory.toString(), "ruta", "path", "storage");

        var downloaded = get(route(number) + "/" + id, owner);
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(PDF);
        assertThat(downloaded.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(downloaded.headers().firstValue("Content-Disposition").orElseThrow())
                .startsWith("attachment").contains("filename*=").doesNotContain(directory.toString());
        assertThat(downloaded.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertUnchanged(number);
    }

    @ParameterizedTest
    @CsvSource({"bad.exe,application/pdf,pdf,400", "bad.pdf,image/png,pdf,400",
            "bad.pdf,application/pdf,png,400", "empty.pdf,application/pdf,empty,400",
            "large.pdf,application/pdf,large,413", "../escape.pdf,application/pdf,pdf,400"})
    void invalidBatchLeavesNoRowsOrFiles(String name, String mime, String fixture, int expected) throws Exception {
        var owner = user("RADICADOR", "RADICACION");
        long number = offer(owner);
        byte[] bytes = switch (fixture) {
            case "png" -> PNG;
            case "empty" -> new byte[0];
            case "large" -> new byte[1025];
            default -> PDF;
        };
        var response = upload(number, owner, new Part("valid.pdf", "application/pdf", PDF),
                new Part(name, mime, bytes));
        assertThat(response.statusCode()).as(asText(response)).isEqualTo(expected);
        assertThat(count(number)).isZero();
        assertThat(diskFiles()).isEmpty();
        assertUnchanged(number);
    }

    @Test
    void protectsUploadListAndDownloadAndDoesNotResolveAnIdAcrossOffers() throws Exception {
        long owner = user("RADICADOR", "RADICACION");
        long outsider = user("RADICADOR", "RADICACION");
        long noModule = user("RADICADOR", null);
        long first = offer(owner);
        long second = offer(owner);
        var part = new Part("sample.pdf", "application/pdf", PDF);
        assertThat(upload(first, outsider, part).statusCode()).isEqualTo(404);
        assertThat(upload(first, noModule, part).statusCode()).isEqualTo(403);
        assertThat(upload(Long.MAX_VALUE, owner, part).statusCode()).isEqualTo(404);
        var created = upload(first, owner, part);
        assertThat(created.statusCode()).isEqualTo(201);
        var id = json.readTree(created.body()).get(0).path("id").asString();
        assertThat(get(route(first), outsider).statusCode()).isEqualTo(404);
        assertThat(get(route(first) + "/" + id, outsider).statusCode()).isEqualTo(404);
        assertThat(get(route(first) + "/" + id, null).statusCode()).isEqualTo(401);
        assertThat(get(route(second) + "/" + id, owner).statusCode()).isEqualTo(404);
        assertThat(get(route(first) + "/" + UUID.randomUUID(), owner).statusCode()).isEqualTo(404);
        assertThat(count(first)).isEqualTo(1);
        assertThat(count(second)).isZero();
    }

    @Test
    void appliesAssignedAnalystEscalatedChiefAndAdministratorTrackingPermissions() throws Exception {
        long owner = user("RADICADOR", "RADICACION");
        long assigned = user("ANALISTA", "ANALISIS");
        long otherAnalyst = user("ANALISTA", "ANALISIS");
        long chief = user("JEFE", "APROBACION");
        long administrator = user("ADMINISTRADOR", "SEGUIMIENTO");
        long adminWithoutTracking = user("ADMINISTRADOR", "ADMINISTRACION");
        long number = offer(owner);
        assertThat(get(route(number), assigned).statusCode()).isEqualTo(403);
        assertThat(get(route(number), chief).statusCode()).isEqualTo(403);
        assertThat(get(route(number), administrator).statusCode()).isEqualTo(200);
        assertThat(get(route(number), adminWithoutTracking).statusCode()).isEqualTo(403);

        jdbc.update("""
                UPDATE oferta SET estado = 'ESCALADO', analista_id = ?, modificado_en = now(), escalado_en = now()
                WHERE numero_radicacion = ?
                """, assigned, number);
        assertThat(get(route(number), assigned).statusCode()).isEqualTo(200);
        assertThat(get(route(number), otherAnalyst).statusCode()).isEqualTo(403);
        assertThat(get(route(number), chief).statusCode()).isEqualTo(200);
        var jwt = token(assigned);
        jdbc.update("DELETE FROM usuario_modulo WHERE usuario_id = ?", assigned);
        assertThat(getWithToken(route(number), jwt).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", assigned);
        assertThat(getWithToken(route(number), jwt).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollsBackMetadataAndCompensatesOrLogsOrphans(boolean failCleanup, CapturedOutput output) throws Exception {
        var owner = user("RADICADOR", "RADICACION");
        long number = offer(owner);
        var prior = upload(number, owner, new Part("prior.pdf", "application/pdf", PDF));
        assertThat(prior.statusCode()).isEqualTo(201);
        String existing = json.readTree(prior.body()).get(0).path("id").asString();
        if (failCleanup) {
            doThrow(new IOException("fixture")).when(storage).delete(any(UUID.class));
        }
        jdbc.execute("""
                ALTER TABLE oferta_adjunto ADD CONSTRAINT test_attachment_reject_fixture
                    CHECK (nombre_archivo <> 'reject-fixture.pdf')
                """);
        try {
            var response = upload(number, owner, new Part("first.pdf", "application/pdf", PDF),
                    new Part("reject-fixture.pdf", "application/pdf", PDF));
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(asText(response)).doesNotContain("reject-fixture", "test_attachment", directory.toString());
            assertThat(count(number)).isEqualTo(1);
            assertThat(diskFiles()).contains(existing);
            if (failCleanup) {
                assertThat(diskFiles()).hasSize(3);
                for (String id : diskFiles()) {
                    if (!id.equals(existing)) {
                        assertThat(output.getAll()).contains("No se pudo retirar el archivo de adjunto " + id);
                    }
                }
            } else {
                assertThat(diskFiles()).containsExactly(existing);
            }
            assertUnchanged(number);
        } finally {
            jdbc.execute("ALTER TABLE oferta_adjunto DROP CONSTRAINT test_attachment_reject_fixture");
        }
    }

    @Test
    void persistenceFailureKeepsFilenameAndOfferIdentifiersOutOfLogs(CapturedOutput output) throws Exception {
        long number = 918704153217L;
        long offerId = 816059237441L;
        long owner = user("RADICADOR", "RADICACION");
        String filename = "brp-log-regression-private-document.pdf";
        jdbc.update("""
                INSERT INTO oferta (id, numero_radicacion, radicador_id, brp_version_id, precio_referencia,
                    valor_total, descuento, estado, creado_en, version) OVERRIDING SYSTEM VALUE
                SELECT ?, ?, ?, id, price, price, 0, 'RADICADO', now(), 0
                FROM brp_catalog WHERE code = 'BRP-0001' ORDER BY ingestion_date DESC LIMIT 1
                """, offerId, number, owner);
        numbers.add(number);
        jdbc.execute("""
                ALTER TABLE oferta_adjunto ADD CONSTRAINT test_attachment_redact_failure
                    CHECK (nombre_archivo <> 'brp-log-regression-private-document.pdf')
                """);
        try {
            var response = upload(number, owner, new Part("valid.pdf", "application/pdf", PDF),
                    new Part(filename, "application/pdf", PDF));
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(asText(response)).doesNotContain(filename, "test_attachment_redact_failure");
            assertThat(output.getAll()).contains("Falló la persistencia de adjuntos (DataIntegrityViolationException)")
                    .doesNotContain(filename, Long.toString(number), Long.toString(offerId), "Failing row contains");
            assertThat(count(number)).isZero();
            assertThat(diskFiles()).isEmpty();
            assertUnchanged(number);
        } finally {
            jdbc.execute("ALTER TABLE oferta_adjunto DROP CONSTRAINT test_attachment_redact_failure");
        }
    }

    private List<String> diskFiles() throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).toList();
        }
    }

    private int count(long number) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM oferta_adjunto a JOIN oferta o ON o.id = a.oferta_id
                WHERE o.numero_radicacion = ?
                """, Integer.class, number);
    }

    private void assertUnchanged(long number) {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM oferta WHERE numero_radicacion = ? AND estado = 'RADICADO'
                AND version = 0 AND analista_id IS NULL AND modificado_en IS NULL
                """, Integer.class, number)).isEqualTo(1);
    }

    private long offer(long owner) {
        long number = jdbc.queryForObject("""
                INSERT INTO oferta (radicador_id, brp_version_id, precio_referencia, valor_total,
                    descuento, estado, creado_en, version)
                SELECT ?, id, price, price, 0, 'RADICADO', now(), 0
                FROM brp_catalog WHERE code = 'BRP-0001' ORDER BY ingestion_date DESC LIMIT 1
                RETURNING numero_radicacion
                """, Long.class, owner);
        numbers.add(number);
        return number;
    }

    private long user(String role, String module) {
        long id = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, identificador_acceso, password_hash, rol)
                SELECT 'Usuario de pruebas', ?, password_hash, ? FROM usuario
                WHERE identificador_acceso = 'admin' RETURNING id
                """, Long.class, "attachment-test-" + UUID.randomUUID(), role);
        users.add(id);
        if (module != null) {
            jdbc.update("INSERT INTO usuario_modulo VALUES (?, ?)", id, module);
        }
        return id;
    }

    private String token(long actor) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(Long.toString(actor)).issuer(IdentityAuthentication.ISSUER)
                .audience(List.of(IdentityAuthentication.AUDIENCE)).issuedAt(now)
                .expiresAt(now.plus(IdentityAuthentication.TOKEN_LIFETIME)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private HttpResponse<byte[]> upload(long number, long actor, Part... files) throws Exception {
        String boundary = UUID.randomUUID().toString();
        var bytes = new ByteArrayOutputStream();
        for (var file : files) {
            bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\""
                    + file.name() + "\"\r\nContent-Type: " + file.mime() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            bytes.write(file.bytes());
            bytes.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        bytes.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        var request = request(route(number), token(actor)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> get(String path, Long actor) throws Exception {
        return getWithToken(path, actor == null ? null : token(actor));
    }

    private HttpResponse<byte[]> getWithToken(String path, String token) throws Exception {
        return client.send(request(path, token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest.Builder request(String path, String token) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private static String route(long number) {
        return "/api/offers/" + number + "/attachments";
    }

    private static String asText(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private record Part(String name, String mime, byte[] bytes) {
    }
}
