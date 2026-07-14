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
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

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
class WithdrawalKafkaProducerTest {

    private static final String TOPIC = "test-topic";
    private static final long TIMEOUT = 5000L;

    @Mock
    private KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;

    private WithdrawalKafkaProducer producer;

    @Mock
    private KafkaProducerEventFactory kafkaProducerEventFactory;

    @BeforeEach
    void setUp() {
        producer = new WithdrawalKafkaProducer(
                kafkaTemplate,
                kafkaProducerEventFactory,
                TIMEOUT);
    }

    @Test
    void publishWithdrawalEvent_whenRequestIsValid_succeeds() {
        WithdrawalDocument document = buildDocument();
        when(kafkaTemplate.send(
                ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        when(kafkaProducerEventFactory.createProducerRecord(
                eq(document.getWithdrawalId()),
                eq(document.getCompanyNumber()),
                eq(document.getPartnerOrganisation()),
                any(EventType.class)))
                .thenReturn(
                        new ProducerRecord<>(
                                TOPIC,
                                document.getWithdrawalId(),
                                StrikeOffPartnerObjections.newBuilder()
                                        .setEventId("event-id")
                                        .setEventTime(Instant.now().toString())
                                        .setSource("test")
                                        .setEventType(EventType.WITHDRAWAL)
                                        .setCompanyNumber(document.getCompanyNumber())
                                        .setPartnerOrganisation(document.getPartnerOrganisation())
                                        .setStrikeOffEventId(document.getWithdrawalId())
                                        .build()
                        )
                );


        assertDoesNotThrow(() ->
                producer.publishWithdrawalEvent(document));

        verify(kafkaTemplate)
                .send(ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any());
    }

    @Test
    void publishWithdrawalEvent_whenKafkaFails_throwsKafkaPublishException() {
        WithdrawalDocument document = buildDocument();
        when(kafkaTemplate.send(ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Kafka failure")));
        when(kafkaProducerEventFactory.createProducerRecord(
                eq(document.getWithdrawalId()),
                eq(document.getCompanyNumber()),
                eq(document.getPartnerOrganisation()),
                any(EventType.class)))
                .thenReturn(new ProducerRecord<>(TOPIC, document.getWithdrawalId(), new StrikeOffPartnerObjections()));

        assertThrows(
                KafkaPublishException.class,
                () -> producer.publishWithdrawalEvent(document));

    }

    @Test
    void publishWithdrawalEvent_whenKafkaFailsWithInterruptedException_throwsKafkaPublishException() {
        WithdrawalDocument document = buildDocument();
        when(kafkaTemplate.send(ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new InterruptedException()));
        when(kafkaProducerEventFactory.createProducerRecord(
                eq(document.getWithdrawalId()),
                eq(document.getCompanyNumber()),
                eq(document.getPartnerOrganisation()),
                any(EventType.class)))
                .thenReturn(new ProducerRecord<>(TOPIC, document.getWithdrawalId(), new StrikeOffPartnerObjections()));
        assertThrows(
                KafkaPublishException.class,
                () -> producer.publishWithdrawalEvent(document));
    }

    private WithdrawalDocument buildDocument() {
        WithdrawalDocument document = new WithdrawalDocument();
        document.setWithdrawalId("withdrawal-123");
        document.setCompanyNumber("12345678");
        document.setPartnerOrganisation("TEST_PARTNER");
        return document;
    }
}