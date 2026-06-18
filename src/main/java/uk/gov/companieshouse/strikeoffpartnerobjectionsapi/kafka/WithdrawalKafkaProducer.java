package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Component
public class WithdrawalKafkaProducer {
    private final KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;
    private final String topic;
    private final long timeoutMilliseconds;

    public WithdrawalKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            @Value("${kafka.topic.strikeoff.partner.objections}") String topic,
            @Value("${kafka.max-block-milliseconds}") long timeoutMilliseconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.timeoutMilliseconds = timeoutMilliseconds;
    }

    public void publishWithdrawalEvent(WithdrawalDocument withdrawalDocument) {
        ProducerRecord<String, StrikeOffPartnerObjections> withdrawalRecord = mapToRecord(withdrawalDocument);

        LOGGER.info(String.format("Sending WITHDRAWAL event to topic: %s, objectionId: %s",
                topic, withdrawalDocument.getWithdrawalId()));

        try {
            kafkaTemplate.send(withdrawalRecord).get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
            LOGGER.info("Successfully sent WITHDRAWAL event: " + withdrawalDocument.getWithdrawalId());
        } catch (ExecutionException | TimeoutException | InterruptedException ex) {
            if (ex.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KafkaPublishException("Failed to send Kafka message for objection: "
                    + withdrawalDocument.getWithdrawalId(), ex);
        }
    }

    private ProducerRecord<String, StrikeOffPartnerObjections> mapToRecord(WithdrawalDocument doc) {
        StrikeOffPartnerObjections message = StrikeOffPartnerObjections.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTime(Instant.now().toString())
                .setSource("strike-off-partner-objections-api")
                .setEventType(EventType.WITHDRAWAL)
                .setPartnerOrganisation(doc.getPartnerOrganisation())
                .setStrikeOffEventId(doc.getWithdrawalId())  // withdrawalId maps to strike_off_event__id
                .build();
        return new ProducerRecord<>(topic, doc.getWithdrawalId(), message);
    }
}
