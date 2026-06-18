package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalKafkaProducerTest {

    private static final String TOPIC = "test-topic";
    private static final long TIMEOUT = 5000L;

    @Mock
    private KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;

    private WithdrawalKafkaProducer producer;

    @BeforeEach
    void setUp() {
        producer = new WithdrawalKafkaProducer(
                kafkaTemplate,
                TOPIC,
                TIMEOUT);
    }

    @Test
    void publishWithdrawalEventSuccess() {
        WithdrawalDocument document = buildDocument();
        when(kafkaTemplate.send(
                ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() ->
                producer.publishWithdrawalEvent(document));

        verify(kafkaTemplate)
                .send(ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any());
    }

    @Test
    void publishWithdrawalEventWhenKafkaFailsThrowsKafkaPublishException() {
        WithdrawalDocument document = buildDocument();
        when(kafkaTemplate.send(ArgumentMatchers.<ProducerRecord<String, StrikeOffPartnerObjections>>any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Kafka failure")));

        assertThrows(
                KafkaPublishException.class,
                () -> producer.publishWithdrawalEvent(document));

    }

    private WithdrawalDocument buildDocument() {
        WithdrawalDocument document = new WithdrawalDocument();
        document.setWithdrawalId("withdrawal-123");
        document.setPartnerOrganisation("TEST_PARTNER");
        return document;
    }
}