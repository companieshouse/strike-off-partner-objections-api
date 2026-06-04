package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class MongoDbTestContainerConfiguration {

    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0")
    );
    private static boolean mongoContainerStarted;

    static {
        try {
            mongoDBContainer.start();
            mongoContainerStarted = true;
        } catch (RuntimeException ex) {
            mongoContainerStarted = false;
        }
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        if (mongoContainerStarted) {
            registry.add("spring.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        }
    }

}
