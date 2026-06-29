package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerEventFactoryTest {

    @Test
    void shouldCreateProducerRecord() {
        // given
        String topic = "test-topic";
        String documentId = "12345";
        String partnerOrganisation = "PartnerA";
        String eventCorrelationId = "corr-123";

        KafkaProducerEventFactory factory =
                new KafkaProducerEventFactory(topic);

        // when
        ProducerRecord<String, StrikeOffPartnerObjections> producerRecord =
                factory.createProducerRecord(
                        documentId,
                        partnerOrganisation,
                        EventType.WITHDRAWAL,
                        eventCorrelationId);

        // then
        assertThat(producerRecord.topic()).isEqualTo(topic);
        assertThat(producerRecord.key()).isEqualTo(documentId);

        StrikeOffPartnerObjections message = producerRecord.value();

        assertThat(message.getStrikeOffEventId())
                .isEqualTo(documentId);

        assertThat(message.getPartnerOrganisation())
                .isEqualTo(partnerOrganisation);

        assertThat(message.getEventType())
                .isEqualTo(EventType.WITHDRAWAL);

        assertThat(message.getSource())
                .isEqualTo("strike-off-partner-objections-api");

        assertThat(message.getEventId())
                .isEqualTo(eventCorrelationId);

        assertThat(message.getEventTime())
                .isNotBlank();
    }
}
