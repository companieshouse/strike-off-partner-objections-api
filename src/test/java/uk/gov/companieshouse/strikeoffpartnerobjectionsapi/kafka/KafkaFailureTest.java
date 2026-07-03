package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.KafkaException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.companieshouse.api.InternalApiClient;
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
    private static final String DEBT_MANAGEMENT_WORKSTREAM = "debt-management";

    @MockitoBean
    private InternalApiClient internalApiClient;

    @Autowired
    private WithdrawalKafkaProducer withdrawalKafkaProducer;

    @Test
    void publishWithdrawalEvent_whenKafkaUnavailable_throwsKafkaPublishException() {
        WithdrawalDocument document = buildDocument();
        assertThatThrownBy(() ->
                withdrawalKafkaProducer.publishWithdrawalEvent(document))
                .isInstanceOf(KafkaPublishException.class)
                .hasCauseInstanceOf(KafkaException.class);
    }

    private WithdrawalDocument buildDocument() {
        WithdrawalDocument document = new WithdrawalDocument();
        document.setCompanyNumber("12345678");
        document.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
        document.setWithdrawalId(UUID.randomUUID().toString());
        document.setEtag(UUID.randomUUID().toString());
        document.setEventCorrelationId(UUID.randomUUID().toString());
        return document;
    }
}
