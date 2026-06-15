package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class MongoDbIntegration {
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer( DockerImageName.parse( "mongo:7.0.17-jammy" ) );

    @DynamicPropertySource
    public static void setProperties( final DynamicPropertyRegistry registry ) {
        mongoDBContainer.start();
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @BeforeAll
    static void init(){
        mongoDBContainer.start();
    }
}




