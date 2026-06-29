package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit-test")
class ObjectionKafkaProducerTest {

    private static final String TOPIC = "test-topic";
    private static final long TIMEOUT = 6000L;

    @Mock
    private KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;

    private ObjectionKafkaProducer producer;

    @Mock
    private KafkaProducerEventFactory kafkaProducerEventFactory;

    @BeforeEach
    void setUp() {
        producer = new ObjectionKafkaProducer(
                kafkaTemplate,
                kafkaProducerEventFactory,
                TIMEOUT);
    }

    @Test
    void publishObjectionEventSuccess() {
        var document = buildDocument();
        var objectionEvent = getProducerRecord(document);

        when(kafkaTemplate.send(
                ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        when(kafkaProducerEventFactory.createProducerRecord(
                eq(document.getObjectionId()),
                eq(document.getPartnerOrganisation()),
                any(EventType.class),
                eq(document.getEventCorrelationId())))
                .thenReturn(objectionEvent);


        assertDoesNotThrow(() ->
                producer.publishObjectionEvent(document));

        verify(kafkaTemplate).send(objectionEvent);
    }


    @Test
    void publishObjectionEventWhenKafkaSendIsInterruptedThrowsKafkaPublishException() {
        var document = buildDocument();
        when(kafkaTemplate.send(ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new InterruptedException()));
        when(kafkaProducerEventFactory.createProducerRecord(
                eq(document.getObjectionId()),
                eq(document.getPartnerOrganisation()),
                any(EventType.class),
                eq(document.getEventCorrelationId())))
                .thenReturn(new ProducerRecord<>(TOPIC, document.getObjectionId(), new StrikeOffPartnerObjections()));
        assertThrows(
                KafkaPublishException.class,
                () -> producer.publishObjectionEvent(document));
    }


    @Test
    void shouldThrowKafkaPublishExceptionWhenKafkaSendFails() {
        var document = buildDocument();
        when(kafkaTemplate.send(ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Kafka failure")));
        when(kafkaProducerEventFactory.createProducerRecord(
                eq(document.getObjectionId()),
                eq(document.getPartnerOrganisation()),
                any(EventType.class),
                eq(document.getEventCorrelationId())))
                .thenReturn(new ProducerRecord<>(TOPIC, document.getObjectionId(), new StrikeOffPartnerObjections()));

        assertThrows(
                KafkaPublishException.class,
                () -> producer.publishObjectionEvent(document));
    }

    private ObjectionDocument buildDocument() {
        ObjectionDocument document = new ObjectionDocument();
        document.setObjectionId("objection-123");
        document.setPartnerOrganisation("TEST_PARTNER");
        document.setEventCorrelationId("corr-objection-123");
        return document;
    }

    private ProducerRecord<String, StrikeOffPartnerObjections> getProducerRecord(ObjectionDocument document) {
        return new ProducerRecord<>(
                TOPIC,
                document.getObjectionId(),
                StrikeOffPartnerObjections.newBuilder()
                        .setEventId("event-id")
                        .setEventTime(Instant.now().toString())
                        .setSource("test")
                        .setEventType(EventType.OBJECTION)
                        .setPartnerOrganisation(document.getPartnerOrganisation())
                        .setStrikeOffEventId(document.getObjectionId())
                        .build()
        );
    }
}
