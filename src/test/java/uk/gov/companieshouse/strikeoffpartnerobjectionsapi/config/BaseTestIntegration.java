package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import consumer.deserialization.AvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.BaseTestConstants.KAFKA_VERSION;

@Import(TestKafkaConfiguration.class)
public abstract class BaseTestIntegration extends MongoDbIntegration {
    protected static final KafkaContainer kafkaContainer =
            new KafkaContainer(DockerImageName.parse("apache/kafka:" + KAFKA_VERSION));

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    protected InternalApiClient internalApiClient;


    @Autowired
    protected KafkaConsumer<String, byte[]> testConsumer;


    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        kafkaContainer.start();

        registry.add("spring.kafka.bootstrap-servers",
                kafkaContainer::getBootstrapServers);
    }

    /**
     * Polls until {@code expectedCount} records arrive or 10 seconds elapse.
     * Uses AvroDeserializer to decode the raw byte[] payload.
     */
    protected List<StrikeOffPartnerObjections> pollKafkaForEvents(List<String> expectedIds) {
        try (AvroDeserializer<StrikeOffPartnerObjections> deserializer =
                     new AvroDeserializer<>(StrikeOffPartnerObjections.class)) {

            List<StrikeOffPartnerObjections> collected = new ArrayList<>();

            await().atMost(10, SECONDS).until(() -> {
                ConsumerRecords<String, byte[]> records =
                        testConsumer.poll(Duration.ofMillis(500));

                records.forEach(r -> collected.add(deserializer.deserialize(r.topic(), r.value())));

                long matched = collected.stream()
                        .map(StrikeOffPartnerObjections::getStrikeOffEventId)
                        .filter(expectedIds::contains)
                        .count();

                return matched >= expectedIds.size();
            });

            return collected.stream().filter(e -> expectedIds.contains(e.getStrikeOffEventId())).toList();
        }
    }
}
