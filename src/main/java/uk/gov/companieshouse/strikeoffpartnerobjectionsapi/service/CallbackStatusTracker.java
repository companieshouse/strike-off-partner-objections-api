package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.HmrcCallbackFailedException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerRequestDocument;

/**
 * A utility class for tracking the status of callbacks to HMRC.
 */
public final class CallbackStatusTracker {

    private CallbackStatusTracker() {
        // prevent instantiation
    }

    public static void applyResult(
            PartnerRequestDocument document,
            CallbackResult result) {
        document.setCallbackCorrelationId(result.correlationId());
        document.setCallbackStatusChangedAt(Instant.now());
        document.setCallbackStatus(
                result.isSuccess() ? CallbackStatus.SUCCESS : CallbackStatus.FAILED);
        document.setCallbackFailureReason(result.failureReason());
    }

    public static void throwIfCallbackFailed(CallbackResult result) {
        if (!result.isSuccess()) {
            throw new HmrcCallbackFailedException(
                    "HMRC callback failed: " + result.failureReason());
        }
    }

    public static boolean isCallbackRequired(PartnerRequestDocument document) {
        return document.getCallbackStatus() == null || CallbackStatus.FAILED == document.getCallbackStatus();
    }
}
