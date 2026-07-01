package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;

@ExtendWith(MockitoExtension.class)
@Tag("unit-test")
class AbstractKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;

    @AfterEach
    void clearInterruptFlag() {
        boolean wasInterrupted = Thread.interrupted();
        if (wasInterrupted) {
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    void sendMessage_whenPublishSucceeds_returnsPublishedMessage() {
        TestKafkaProducer producer = new TestKafkaProducer(kafkaTemplate, 1000L);
        ProducerRecord<String, StrikeOffPartnerObjections> producerRecord = buildRecord("event-success");

        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        StrikeOffPartnerObjections message = producer.send(producerRecord);

        assertEquals("event-success", message.getEventId());
    }

    @Test
    void sendMessage_whenFutureGetIsInterrupted_throwsKafkaPublishExceptionAndSetsInterruptFlag() {
        TestKafkaProducer producer = new TestKafkaProducer(kafkaTemplate, 1000L);
        ProducerRecord<String, StrikeOffPartnerObjections> producerRecord = buildRecord("event-interrupted");

        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(futureThrowingInterrupted());

        KafkaPublishException ex =
                assertThrows(KafkaPublishException.class, () -> producer.send(producerRecord));

        assertEquals("event-interrupted", ex.getEventId());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void sendMessage_whenExecutionFailsWithInterruptedCause_throwsKafkaPublishExceptionAndSetsInterruptFlag() {
        TestKafkaProducer producer = new TestKafkaProducer(kafkaTemplate, 1000L);
        ProducerRecord<String, StrikeOffPartnerObjections> producerRecord = buildRecord("event-execution-interrupted");

        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.failedFuture(new InterruptedException("interrupted")));

        KafkaPublishException ex =
                assertThrows(KafkaPublishException.class, () -> producer.send(producerRecord));

        assertEquals("event-execution-interrupted", ex.getEventId());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void sendMessage_whenFutureGetTimesOut_throwsKafkaPublishExceptionWithEventId() {
        TestKafkaProducer producer = new TestKafkaProducer(kafkaTemplate, 1000L);
        ProducerRecord<String, StrikeOffPartnerObjections> producerRecord = buildRecord("event-timeout");

        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(futureThrowingTimeout());

        KafkaPublishException ex =
                assertThrows(KafkaPublishException.class, () -> producer.send(producerRecord));

        assertEquals("event-timeout", ex.getEventId());
    }

    @Test
    void sendMessage_whenKafkaTemplateThrowsKafkaException_throwsKafkaPublishExceptionWithEventId() {
        TestKafkaProducer producer = new TestKafkaProducer(kafkaTemplate, 1000L);
        ProducerRecord<String, StrikeOffPartnerObjections> producerRecord = buildRecord("event-kafka-exception");

        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenThrow(new KafkaException("kafka unavailable"));

        KafkaPublishException ex =
                assertThrows(KafkaPublishException.class, () -> producer.send(producerRecord));

        assertEquals("event-kafka-exception", ex.getEventId());
    }

    private ProducerRecord<String, StrikeOffPartnerObjections> buildRecord(String eventId) {
        StrikeOffPartnerObjections message = StrikeOffPartnerObjections.newBuilder()
                .setEventId(eventId)
                .setEventTime("2026-01-01T00:00:00Z")
                .setSource("strike-off-partner-objections-api")
                .setEventType(EventType.OBJECTION)
                .setPartnerOrganisation("hmrc")
                .setStrikeOffEventId("document-id-1")
                .build();

        return new ProducerRecord<>("topic", "document-id-1", message);
    }

    private CompletableFuture<SendResult<String, StrikeOffPartnerObjections>> futureThrowingInterrupted() {
        return new CompletableFuture<>() {
            @Override
            public SendResult<String, StrikeOffPartnerObjections> get(long timeout, TimeUnit unit)
                    throws InterruptedException {
                throw new InterruptedException("interrupted");
            }
        };
    }

    private CompletableFuture<SendResult<String, StrikeOffPartnerObjections>> futureThrowingTimeout() {
        return new CompletableFuture<>() {
            @Override
            public SendResult<String, StrikeOffPartnerObjections> get(long timeout, TimeUnit unit)
                    throws TimeoutException {
                throw new TimeoutException("timed out");
            }
        };
    }

    private static class TestKafkaProducer extends AbstractKafkaProducer {

        private TestKafkaProducer(
                KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
                long timeoutMilliseconds) {
            super(kafkaTemplate, timeoutMilliseconds);
        }

        private StrikeOffPartnerObjections send(ProducerRecord<String, StrikeOffPartnerObjections> producerRecord) {
            return sendMessage(producerRecord);
        }
    }
}

