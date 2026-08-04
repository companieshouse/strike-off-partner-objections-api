package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;

import java.time.Instant;
import java.util.UUID;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.APPLICATION_NAMESPACE;

@Component
public class KafkaProducerEventFactory {

    private final String topic;

    public KafkaProducerEventFactory(
            @Value("${kafka.topic.strikeoff.partner.objections}") String topic) {
        this.topic = topic;
    }

    public ProducerRecord<String, StrikeOffPartnerObjections> createProducerRecord(
            String documentId,
            String companyNumber,
            String partnerOrganisation,
            EventType type) {
        StrikeOffPartnerObjections message = StrikeOffPartnerObjections.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTime(Instant.now().toString())
                .setSource(APPLICATION_NAMESPACE)
                .setEventType(type)
                .setCompanyNumber(companyNumber)
                .setPartnerOrganisation(partnerOrganisation)
                .setStrikeOffEventId(documentId)  // withdrawalId maps to strike_off_event__id
                .build();
        return new ProducerRecord<>(topic, documentId, message);
    }
}
