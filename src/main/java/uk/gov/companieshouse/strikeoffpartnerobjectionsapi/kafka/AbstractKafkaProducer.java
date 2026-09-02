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

/**
 * Base class for Kafka producers that publish {@link StrikeOffPartnerObjections} events.
 *
 * <p>Provides common send logic with synchronous blocking until the message is acknowledged
 * or a configurable timeout expires. Interrupts and execution failures are wrapped in a
 * {@link KafkaPublishException} to propagate event correlation context to callers.</p>
 */
public abstract class AbstractKafkaProducer {
    private static final String SENDING_EVENT_FORMAT = "Sending event:%s to topic: %s, id: %s";
    private static final String SUCCESSFULLY_SENT_FORMAT = "Successfully sent: %s eventId: %s";
    private static final String INTERRUPTED_MESSAGE_PREFIX = "Interrupted while sending Kafka message for ";
    private static final String FAILED_MESSAGE_PREFIX = "Failed to send Kafka message for ";

    final KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;
    final long timeoutMilliseconds;

    /**
     * Constructs the producer with the required Kafka template and send timeout.
     *
     * @param kafkaTemplate       the Spring Kafka template used to send messages
     * @param timeoutMilliseconds the maximum time to wait for send acknowledgement, in milliseconds
     */
    AbstractKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            long timeoutMilliseconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.timeoutMilliseconds = timeoutMilliseconds;
    }

    /**
     * Sends a producer record to the configured Kafka topic and waits for acknowledgement.
     *
     * <p>Blocks until the send is acknowledged or the timeout elapses. On interruption,
     * the current thread's interrupt flag is restored before throwing.</p>
     *
     * @param producerRecord the record to send, including topic, key, and message payload
     * @return the message payload that was sent, for use in event tracking
     * @throws KafkaPublishException if the send is interrupted, times out, or encounters a broker error
     */
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