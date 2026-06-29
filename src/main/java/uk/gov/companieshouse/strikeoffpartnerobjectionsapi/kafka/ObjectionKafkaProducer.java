package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

@Component
public class ObjectionKafkaProducer extends AbstractKafkaProducer {
    private final KafkaProducerEventFactory kafkaProducerEventFactory;

    public ObjectionKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            KafkaProducerEventFactory kafkaProducerEventFactory,
            @Value("${kafka.max-block-milliseconds}") long timeoutMilliseconds) {
        super(kafkaTemplate, timeoutMilliseconds);
        this.kafkaProducerEventFactory = kafkaProducerEventFactory;
    }

    public StrikeOffPartnerObjections publishObjectionEvent(ObjectionDocument objectionDocument) {
        var objectionRecord = kafkaProducerEventFactory.createProducerRecord(
                objectionDocument.getObjectionId(),
                objectionDocument.getPartnerOrganisation(),
                EventType.OBJECTION
        );

        return sendMessage(objectionRecord);
    }
}