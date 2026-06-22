package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Component
public class WithdrawalKafkaProducer {
    private final KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate;
    private final long timeoutMilliseconds;
    private final KafkaProducerEventFactory kafkaProducerEventFactory;

    public WithdrawalKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            KafkaProducerEventFactory kafkaProducerEventFactory,
            @Value("${kafka.max-block-milliseconds}") long timeoutMilliseconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProducerEventFactory = kafkaProducerEventFactory;
        this.timeoutMilliseconds = timeoutMilliseconds;
    }

    public void publishWithdrawalEvent(WithdrawalDocument withdrawalDocument) {
        var withdrawalRecord = kafkaProducerEventFactory.createProducerRecord(
                withdrawalDocument.getWithdrawalId(),
                withdrawalDocument.getPartnerOrganisation(),
                EventType.WITHDRAWAL
        );

        LOGGER.info(String.format("Sending WITHDRAWAL event to topic: %s, objectionId: %s",
                withdrawalRecord.topic(), withdrawalDocument.getWithdrawalId()));

        try {
            kafkaTemplate.send(withdrawalRecord).get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
            LOGGER.info("Successfully sent WITHDRAWAL event: " + withdrawalDocument.getWithdrawalId());
        } catch (ExecutionException | TimeoutException | InterruptedException | KafkaException ex) {
            if (ex.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KafkaPublishException("Failed to send Kafka message for objection: "
                    + withdrawalDocument.getWithdrawalId(), ex);
        }
    }
}
