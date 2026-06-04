package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.MongoDbTestContainerConfiguration;

@SpringBootTest
@Import(MongoDbTestContainerConfiguration.class)
class MongoConfigurationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void mongoTemplateBeanIsCreated() {
        assertNotNull(mongoTemplate);
    }
}