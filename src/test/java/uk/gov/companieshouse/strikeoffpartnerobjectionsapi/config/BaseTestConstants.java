package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

public class BaseTestConstants {
    public static final String MONGO_VERSION = System.getProperty(
            "test.mongo.version",
            "7.0.17-jammy"
    );
    public static final String KAFKA_VERSION = System.getProperty(
            "test.kafka.version",
            "4.0.0"
    );
}
