package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import consumer.serialization.AvroSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;

import java.util.Map;

@Configuration
public class KafkaConfiguration {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.config.acks:all}")
    private String acks;

    @Value("${kafka.max-block-milliseconds:10000}")
    private int maxBlockMilliseconds;

    @Value("${spring.kafka.producer.retries:3}")
    private int maxRetries;

    @Value("${spring.kafka.producer.enable-idempotence:true}")
    private boolean enableIdempotence;

    @Bean
    public KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate() {
        Map<String, Object> configProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.ACKS_CONFIG, acks,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMilliseconds,
                ProducerConfig.RETRIES_CONFIG, maxRetries,
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, enableIdempotence,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, AvroSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroSerializer.class
        );
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configProps));
    }

}
