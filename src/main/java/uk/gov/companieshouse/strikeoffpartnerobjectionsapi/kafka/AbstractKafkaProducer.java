package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

public abstract class AbstractKafkaProducer {
    protected final KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;
    protected final long timeoutMilliseconds;

    protected AbstractKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            long timeoutMilliseconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.timeoutMilliseconds = timeoutMilliseconds;
    }

    protected StrikeOffPartnerObjections sendMessage(ProducerRecord<String, StrikeOffPartnerObjections> producerRecord) {
        var message = producerRecord.value();
        var topic = producerRecord.topic();
        var documentId = producerRecord.key();

        LOGGER.info(String.format("Sending event:%s to topic: %s, id: %s",
                message.getEventType(), topic, documentId));

        try {
            kafkaTemplate.send(producerRecord)
                    .get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
            LOGGER.info(String.format("Successfully sent: %s eventId: %s", message.getEventType(), message.getEventId()));
            return message;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException(
                    "Interrupted while sending Kafka message for " + message.getEventType() + ": " + documentId,
                    message.getEventId(), ex);
        } catch (ExecutionException | TimeoutException | KafkaException ex) {
            if (ex.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KafkaPublishException(
                    "Failed to send Kafka message for " + message.getEventType() + ": " + documentId,
                    message.getEventId(), ex);
        }
    }
}