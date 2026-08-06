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
    private static final String SENDING_EVENT_FORMAT = "Sending event:%s to topic: %s, id: %s";
    private static final String SUCCESSFULLY_SENT_FORMAT = "Successfully sent: %s eventId: %s";
    private static final String INTERRUPTED_MESSAGE_PREFIX = "Interrupted while sending Kafka message for ";
    private static final String FAILED_MESSAGE_PREFIX = "Failed to send Kafka message for ";

    final KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;
    final long timeoutMilliseconds;

    AbstractKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            long timeoutMilliseconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.timeoutMilliseconds = timeoutMilliseconds;
    }

    StrikeOffPartnerObjections sendMessage(ProducerRecord<String, StrikeOffPartnerObjections> producerRecord) {
        StrikeOffPartnerObjections message = producerRecord.value();
        String topic = producerRecord.topic();
        String documentId = producerRecord.key();

        LOGGER.info(String.format(SENDING_EVENT_FORMAT,
                message.getEventType(), topic, documentId));

        try {
            kafkaTemplate.send(producerRecord)
                    .get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
            LOGGER.info(String.format(SUCCESSFULLY_SENT_FORMAT, message.getEventType(), message.getEventId()));
            return message;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException(
                    INTERRUPTED_MESSAGE_PREFIX + message.getEventType() + ": " + documentId,
                    message.getEventId(), ex);
        } catch (ExecutionException | TimeoutException | KafkaException ex) {
            if (ex.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KafkaPublishException(
                    FAILED_MESSAGE_PREFIX + message.getEventType() + ": " + documentId,
                    message.getEventId(), ex);
        }
    }
}