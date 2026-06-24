package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

public class AbstractKafkaProducer {
    protected final KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;
    protected final long timeoutMilliseconds;

    public AbstractKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            long timeoutMilliseconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.timeoutMilliseconds = timeoutMilliseconds;
    }

    protected void sendMessage(String topic, String documentId,
                               StrikeOffPartnerObjections message, EventType eventType) {
        LOGGER.info(String.format("Sending event:%s to topic: %s, id: %s",
                eventType, topic, documentId));

        try {
            kafkaTemplate.send(topic, documentId, message)
                    .get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
            LOGGER.info(String.format("Successfully sent: %s eventId: %s", eventType, documentId));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException(
                    "Interrupted while sending Kafka message for " + eventType + ": " + documentId, ex);
        } catch (ExecutionException | TimeoutException | KafkaException ex) {
            if (ex.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KafkaPublishException(
                    "Failed to send Kafka message for " + eventType + ": " + documentId, ex);
        }
    }
}