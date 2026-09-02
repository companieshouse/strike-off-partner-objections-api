package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;

import java.time.Instant;
import java.util.UUID;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.APPLICATION_NAMESPACE;

/**
 * Factory for constructing Kafka {@link ProducerRecord} instances containing
 * {@link StrikeOffPartnerObjections} Avro messages.
 *
 * <p>Each record is assigned a unique event ID, the current timestamp, the application
 * as the source, and the provided event type, company number, and partner organisation.</p>
 */
@Component
public class KafkaProducerEventFactory {

    private final String topic;

    /**
     * Constructs the factory with the Kafka topic name.
     *
     * @param topic the Kafka topic to which all records produced by this factory will be sent
     */
    public KafkaProducerEventFactory(
            @Value("${kafka.topic.strikeoff.partner.objections}") String topic) {
        this.topic = topic;
    }

    /**
     * Creates a Kafka {@link ProducerRecord} containing a {@link StrikeOffPartnerObjections} message.
     *
     * @param documentId          the unique ID of the document (objection or withdrawal), used as the record key
     * @param companyNumber       the company number associated with the event
     * @param partnerOrganisation the partner organisation originating the event
     * @param type                the event type, either {@link uk.gov.companieshouse.strikeoff.partner.objections.EventType#OBJECTION}
     *                            or {@link uk.gov.companieshouse.strikeoff.partner.objections.EventType#WITHDRAWAL}
     * @return a producer record ready to be sent to the Kafka topic
     */
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
