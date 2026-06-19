package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.KafkaException;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.producer.retries=2",
        "kafka.max-block-milliseconds=300"
})
@Tag("unit-test")
class KafkaFailureTest {
    @Autowired
    private WithdrawalKafkaProducer withdrawalKafkaProducer;

    @Test
    void shouldThrowKafkaPublishExceptionWhenKafkaUnavailable() {
        WithdrawalDocument document = buildDocument();
        assertThatThrownBy(() ->
                withdrawalKafkaProducer.publishWithdrawalEvent(document))
                .isInstanceOf(KafkaPublishException.class)
                .hasCauseInstanceOf(KafkaException.class);
    }

    private WithdrawalDocument buildDocument() {
        WithdrawalDocument document = new WithdrawalDocument();
        document.setCompanyNumber("12345678");
        document.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT.getValue());
        document.setWithdrawalId(UUID.randomUUID().toString());
        document.setEtag(UUID.randomUUID().toString());
        return document;
    }
}
