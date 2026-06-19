package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Import(TestKafkaConfiguration.class)
public abstract class BaseTestIntegration extends MongoDbIntegration {
    protected static final KafkaContainer kafkaContainer =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));


    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        kafkaContainer.start();

        registry.add("spring.kafka.bootstrap-servers",
                kafkaContainer::getBootstrapServers);
    }
}
