package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mongo.enabled=false",
                "management.endpoints.web.path-mapping.health=healthcheck"
        }
)
class StrikeOffPartnerObjectionsApiApplicationTests {

    @LocalManagementPort
    private int managementPort;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void contextLoads() {
    }

    @Test
    void healthcheckEndpointReturnsUp() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + managementPort + "/strike-off-partner-objections-api/healthcheck"))
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void defaultActuatorHealthPathIsNotExposed() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + managementPort + "/actuator/health"))
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);
    }
}