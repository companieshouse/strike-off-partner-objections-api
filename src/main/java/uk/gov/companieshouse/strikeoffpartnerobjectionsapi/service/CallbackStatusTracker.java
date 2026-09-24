package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerRequestDocument;

/**
 * Utility for tracking HMRC callback status against a {@link PartnerRequestDocument}.
 *
 * <p>Records the callback status (SUCCESS, FAILED), correlation ID for message
 * traceability, timestamp of last status change, and failure reason where applicable.
 * This information supports investigation and diagnostics of callback failures.</p>
 */
public final class CallbackStatusTracker {

    private CallbackStatusTracker() {
        // Private constructor to hide the implicit public one
    }

    /**
     * Marks a document as having successfully received callback notification.
     *
     * @param document             the partner request document to update
     * @param callbackCorrelationId the unique correlation ID from the callback attempt
     */
    public static void markCallbackSuccess(PartnerRequestDocument document, String callbackCorrelationId) {
        document.setCallbackCorrelationId(callbackCorrelationId);
        document.setCallbackStatus(CallbackStatus.SUCCESS);
        document.setCallbackStatusChangedAt(Instant.now());
        document.setCallbackFailureReason(null);
    }

    /**
     * Marks a document as having failed to send callback notification.
     *
     * @param document             the partner request document to update
     * @param callbackCorrelationId the unique correlation ID from the failed callback attempt
     * @param failureReason        a human-readable description of why the callback failed
     */
    public static void markCallbackFailed(PartnerRequestDocument document, String callbackCorrelationId,
                                          String failureReason) {
        document.setCallbackCorrelationId(callbackCorrelationId);
        document.setCallbackStatus(CallbackStatus.FAILED);
        document.setCallbackStatusChangedAt(Instant.now());
        document.setCallbackFailureReason(failureReason);
    }
}
