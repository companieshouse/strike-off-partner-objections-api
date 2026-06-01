package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/strike_off_partner_objections"
})
class MongoConfigurationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void mongoTemplateBeanIsCreated() {
        assertNotNull(mongoTemplate);
    }
}