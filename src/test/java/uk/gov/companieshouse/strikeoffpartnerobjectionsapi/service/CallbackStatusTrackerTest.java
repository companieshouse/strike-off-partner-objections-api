package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.HmrcCallbackFailedException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerRequestDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit-test")
class CallbackStatusTrackerTest {

    private static final String CORRELATION_ID = "correlation-123";
    private static final String FAILURE_REASON = "Connection timeout";

    @ParameterizedTest
    @MethodSource("documents")
    void applyResult_whenSuccessful_recordsSuccessAndClearsPreviousFailure(
            PartnerRequestDocument document) {
        document.setCallbackStatus(CallbackStatus.FAILED);
        document.setCallbackFailureReason(FAILURE_REASON);
        Instant before = Instant.now();

        CallbackStatusTracker.applyResult(
                document, CallbackResult.success(CORRELATION_ID));

        assertThat(document.getCallbackStatus()).isEqualTo(CallbackStatus.SUCCESS);
        assertThat(document.getCallbackCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(document.getCallbackFailureReason()).isNull();
        assertThat(document.getCallbackStatusChangedAt())
                .isBetween(before, Instant.now());
    }

    @ParameterizedTest
    @MethodSource("documents")
    void applyResult_whenFailed_recordsFailureAndClearsPreviousCorrelationId(
            PartnerRequestDocument document) {
        document.setCallbackStatus(CallbackStatus.SUCCESS);
        document.setCallbackCorrelationId(CORRELATION_ID);
        Instant before = Instant.now();

        CallbackStatusTracker.applyResult(
                document, CallbackResult.failure(FAILURE_REASON));

        assertThat(document.getCallbackStatus()).isEqualTo(CallbackStatus.FAILED);
        assertThat(document.getCallbackCorrelationId()).isNull();
        assertThat(document.getCallbackFailureReason()).isEqualTo(FAILURE_REASON);
        assertThat(document.getCallbackStatusChangedAt())
                .isBetween(before, Instant.now());
    }

    @Test
    void throwIfCallbackFailed_whenSuccessful_doesNotThrow() {
        CallbackResult result = CallbackResult.success(CORRELATION_ID);

        assertThatCode(() -> CallbackStatusTracker.throwIfCallbackFailed(result))
                .doesNotThrowAnyException();
    }

    @Test
    void throwIfCallbackFailed_whenFailed_throwsCallbackException() {
        CallbackResult result = CallbackResult.failure(FAILURE_REASON);

        assertThatThrownBy(() -> CallbackStatusTracker.throwIfCallbackFailed(result))
                .isInstanceOf(HmrcCallbackFailedException.class)
                .hasMessage("HMRC callback failed: " + FAILURE_REASON);
    }

    private static Stream<PartnerRequestDocument> documents() {
        return Stream.of(new ObjectionDocument(), new WithdrawalDocument());
    }
}