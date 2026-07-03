package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.companieshouse.api.InternalApiClient;

@Tag("integration-test")
@SpringBootTest
class MongoConfigurationTest {

    @MockitoBean
    private InternalApiClient internalApiClient;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void mongoTemplateBean_whenContextLoads_isCreated() {
        assertNotNull(mongoTemplate);
    }
}