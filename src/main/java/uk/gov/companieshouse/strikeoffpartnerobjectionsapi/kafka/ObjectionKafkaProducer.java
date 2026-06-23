package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Component
public class ObjectionKafkaProducer {
    private final KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;
    private final long timeoutMilliseconds;
    private final KafkaProducerEventFactory kafkaProducerEventFactory;

    public ObjectionKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            KafkaProducerEventFactory kafkaProducerEventFactory,
            @Value("${kafka.max-block-milliseconds}") long timeoutMilliseconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProducerEventFactory = kafkaProducerEventFactory;
        this.timeoutMilliseconds = timeoutMilliseconds;
    }

    public void publishObjectionEvent(ObjectionDocument objectionDocument) {
        var objectionRecord = kafkaProducerEventFactory.createProducerRecord(
                objectionDocument.getObjectionId(),
                objectionDocument.getPartnerOrganisation(),
                EventType.OBJECTION
        );

        LOGGER.info(String.format("Sending OBJECTION event to topic: %s, objectionId: %s",
                objectionRecord.topic(), objectionDocument.getObjectionId()));

        try {
            kafkaTemplate.send(objectionRecord).get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
            LOGGER.info("Successfully sent OBJECTION event: " + objectionDocument.getObjectionId());
        } catch (ExecutionException | TimeoutException | InterruptedException | KafkaException ex) {
            if (ex.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KafkaPublishException("Failed to send Kafka message for objection: "
                    + objectionDocument.getObjectionId(), ex);
        }
    }
}
