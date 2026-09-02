package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

/**
 * Kafka producer for publishing withdrawal events.
 *
 * <p>Builds a {@link StrikeOffPartnerObjections} Avro message with event type
 * {@link EventType#WITHDRAWAL} and sends it to the configured topic.</p>
 */
@Component
public class WithdrawalKafkaProducer extends AbstractKafkaProducer {
    private final KafkaProducerEventFactory kafkaProducerEventFactory;

    /**
     * Constructs the producer with the required Kafka template, event factory, and timeout.
     *
     * @param kafkaTemplate             the Spring Kafka template used to send messages
     * @param kafkaProducerEventFactory factory for building {@link StrikeOffPartnerObjections} producer records
     * @param timeoutMilliseconds       the maximum time to wait for send acknowledgement, in milliseconds
     */
    public WithdrawalKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            KafkaProducerEventFactory kafkaProducerEventFactory,
            @Value("${kafka.max-block-milliseconds}") long timeoutMilliseconds) {
        super(kafkaTemplate, timeoutMilliseconds);
        this.kafkaProducerEventFactory = kafkaProducerEventFactory;
    }

    /**
     * Publishes a withdrawal event for the given withdrawal document to Kafka.
     *
     * @param withdrawal the withdrawal document containing the identifiers and metadata for the event
     * @return the published {@link StrikeOffPartnerObjections} message, including the assigned event ID
     * @throws uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException if the event cannot be sent within the configured timeout
     */
    public StrikeOffPartnerObjections publishWithdrawalEvent(WithdrawalDocument withdrawal) {
        ProducerRecord<String, StrikeOffPartnerObjections> withdrawalRecord = kafkaProducerEventFactory.createProducerRecord(
                withdrawal.getWithdrawalId(),
                withdrawal.getCompanyNumber(),
                withdrawal.getPartnerOrganisation(),
                EventType.WITHDRAWAL
        );

        return sendMessage(withdrawalRecord);
    }
}