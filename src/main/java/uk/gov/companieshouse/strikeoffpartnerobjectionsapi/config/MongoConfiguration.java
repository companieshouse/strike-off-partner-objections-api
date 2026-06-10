package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * MongoDB configuration for the Strike Off Partner Objections API.
 * Enables MongoDB auditing with a custom {@link DateTimeProvider} that supplies * the current UTC timestamp as an {@link Instant} for audited fields such as{@code @CreatedDate} and {@code @LastModifiedDate}.
 * Using {@link Instant} ensures timestamps are stored as BSON Date (UTC epoch milliseconds) * in MongoDB, avoiding timezone ambiguity.
 *
 */
@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "mongodbDatetimeProvider")
public class MongoConfiguration {

    /**
     * Provides the current UTC instant for MongoDB auditing.
     * @return a {@link DateTimeProvider} that returns the current UTC time as an {@link Instant}
     */
    @Bean(name = "mongodbDatetimeProvider")
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}

