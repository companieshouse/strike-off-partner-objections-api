package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.path-mapping.health=healthcheck",
                "management.endpoint.health.probes.enabled=false",
                "management.endpoint.health.show-details=always"
        }
)
@AutoConfigureMockMvc
class StrikeOffPartnerObjectionsApiApplicationTests {

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        String mongoUrl = System.getenv("MONGODB_URL");
        registry.add("spring.mongodb.uri", () -> Objects.requireNonNullElse(mongoUrl, "mongodb://localhost:27017/strike_off_partner_objections"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthcheckEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/strike-off-partner-objections-api/healthcheck"))
                .andDo(result -> {
                    String body = result.getResponse().getContentAsString();
                    System.out.println("Health response: " + body);
                })
                .andExpect(status().isOk());
    }

    @Test
    void defaultActuatorHealthPathIsNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }
}