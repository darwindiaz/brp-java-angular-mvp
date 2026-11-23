package co.brp.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import co.brp.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "brp.identity.login.max-attempts=2")
class LoginRateLimitIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private JsonMapper json;

    @Test
    void existingAndUnknownAccountsShareTheSameLimitAndResponse() throws Exception {
        var wrongPassword = UUID.randomUUID().toString();
        var unknownLogin = "unknown-" + UUID.randomUUID();
        try (var client = HttpClient.newHttpClient()) {
            var existing = login(client, "admin", wrongPassword);
            var unknown = login(client, unknownLogin, wrongPassword);
            assertThat(existing.statusCode()).isEqualTo(401);
            assertThat(unknown.statusCode()).isEqualTo(401);
            assertThat(existing.body()).isEqualTo(unknown.body());

            var blockedExisting = login(client, "admin", wrongPassword);
            var blockedUnknown = login(client, unknownLogin, wrongPassword);
            assertThat(blockedExisting.statusCode()).isEqualTo(429);
            assertThat(blockedUnknown.statusCode()).isEqualTo(429);
            assertThat(blockedExisting.body()).isEqualTo(blockedUnknown.body());
            assertThat(blockedExisting.body()).doesNotContain(wrongPassword, unknownLogin);
        }
    }

    private HttpResponse<String> login(HttpClient client, String login, String password) throws Exception {
        var body = json.writeValueAsString(Map.of("login", login, "password", password));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
