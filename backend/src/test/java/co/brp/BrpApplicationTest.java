package co.brp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrpApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void connectsToPostgres() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL ");
    }

    @Test
    void exposesHealthWithoutConnectionDetails() throws Exception {
        var response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"")
                .doesNotContain("\"components\"", "\"details\"");
    }

    @Test
    void deniesOtherManagementEndpoints() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> get(String path) throws Exception {
        var uri = URI.create("http://127.0.0.1:" + port + path);
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
