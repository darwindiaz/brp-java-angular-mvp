package co.brp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import co.brp.testing.PostgresIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import liquibase.integration.spring.SpringLiquibase;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrpApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HikariDataSource dataSource;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private SpringLiquibase liquibase;

    @Test
    void connectsToPostgres() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL ");
        assertThat(dataSource.getJdbcUrl()).isEqualTo(System.getenv("BRP_TEST_JDBC_URL"));
        assertThat(dataSource.getUsername()).isEqualTo(System.getenv("BRP_TEST_DB_USER"));
        assertThat(jdbc.getDataSource()).isSameAs(dataSource);
        assertThat(entityManagerFactory).isInstanceOfSatisfying(EntityManagerFactoryInfo.class,
                factory -> assertThat(factory.getDataSource()).isSameAs(dataSource));
        assertThat(liquibase.getDataSource()).isSameAs(dataSource);
        try (var entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.unwrap(Session.class).doWork(connection -> {
                assertThat(connection.getMetaData().getURL()).isEqualTo(dataSource.getJdbcUrl());
                assertThat(connection.getMetaData().getUserName()).isEqualTo(dataSource.getUsername());
            });
        }
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog", Long.class))
                .isGreaterThanOrEqualTo(3);
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
