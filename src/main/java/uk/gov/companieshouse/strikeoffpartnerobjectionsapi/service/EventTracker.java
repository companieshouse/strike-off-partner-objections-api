package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerRequestDocument;

/**
 * Utility for tracking Kafka event status against a {@link PartnerRequestDocument}.
 *
 * <p>Records the event status (PENDING, PUBLISHED, FAILED), correlation ID for message
 * traceability, timestamp of last status change, and failure reason where applicable.
 * This information supports retry logic and error diagnostics.</p>
 */
public final class EventTracker {

    private EventTracker() {
        // Private constructor to hide the implicit public one
    }

    /**
     * Marks a document as pending — the initial state before a Kafka event is published.
     *
     * @param document the partner request document to update
     */
    public static void markPending(PartnerRequestDocument document) {
        document.setEventStatus(EventStatus.PENDING);
        document.setEventStatusChangedAt(Instant.now());
    }

    /**
     * Marks a document as successfully published and records the Kafka event correlation ID.
     *
     * @param document             the partner request document to update
     * @param eventCorrelationId   the unique event ID assigned by the Kafka producer upon successful send
     */
    public static void markPublished(PartnerRequestDocument document, String eventCorrelationId) {
        document.setEventCorrelationId(eventCorrelationId);
        document.setEventStatus(EventStatus.PUBLISHED);
        document.setEventStatusChangedAt(Instant.now());
        document.setEventFailureReason(null);
    }

    /**
     * Marks a document as failed and records the correlation ID and failure reason.
     *
     * @param document             the partner request document to update
     * @param eventCorrelationId   the unique event ID associated with the failed publish attempt
     * @param failureReason        a human-readable description of why the event failed to publish
     */
    public static void markFailed(PartnerRequestDocument document, String eventCorrelationId, String failureReason) {
        document.setEventCorrelationId(eventCorrelationId);
        document.setEventStatus(EventStatus.FAILED);
        document.setEventStatusChangedAt(Instant.now());
        document.setEventFailureReason(failureReason);
    }
}
