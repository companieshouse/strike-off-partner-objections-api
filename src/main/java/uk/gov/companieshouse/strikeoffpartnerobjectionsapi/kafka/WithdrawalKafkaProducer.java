package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

@Component
public class WithdrawalKafkaProducer extends AbstractKafkaProducer {
    private final KafkaProducerEventFactory kafkaProducerEventFactory;

    public WithdrawalKafkaProducer(
            KafkaTemplate<String, StrikeOffPartnerObjections> kafkaTemplate,
            KafkaProducerEventFactory kafkaProducerEventFactory,
            @Value("${kafka.max-block-milliseconds}") long timeoutMilliseconds) {
        super(kafkaTemplate, timeoutMilliseconds);
        this.kafkaProducerEventFactory = kafkaProducerEventFactory;
    }

    public StrikeOffPartnerObjections publishWithdrawalEvent(WithdrawalDocument withdrawalDocument) {
        var withdrawalRecord = kafkaProducerEventFactory.createProducerRecord(
                withdrawalDocument.getWithdrawalId(),
                withdrawalDocument.getCompanyNumber(),
                withdrawalDocument.getPartnerOrganisation(),
                EventType.WITHDRAWAL
        );

        return sendMessage(withdrawalRecord);
    }
}