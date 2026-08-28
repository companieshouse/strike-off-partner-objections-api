package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerRequestDocument;

/**
 * Simple utility to track event status for retry and error diagnostics.
 * Tracks: event status, correlation ID for message tracking, timestamps, and failure reasons.
 */
public final class EventTracker {

    private EventTracker() {
        // Private constructor to hide the implicit public one
    }

    /**
     * Mark a document as pending (initial state before publishing).
     */
    public static void markPending(PartnerRequestDocument document) {
        document.setEventStatus(EventStatus.PENDING);
        document.setEventStatusChangedAt(Instant.now());
    }

    /**
     * Mark a document as successfully published with the given event correlation ID.
     */
    public static void markPublished(PartnerRequestDocument document, String eventCorrelationId) {
        document.setEventCorrelationId(eventCorrelationId);
        document.setEventStatus(EventStatus.PUBLISHED);
        document.setEventStatusChangedAt(Instant.now());
        document.setEventFailureReason(null);
    }

    /**
     * Mark a document as failed with the given event correlation ID and failure reason.
     */
    public static void markFailed(PartnerRequestDocument document, String eventCorrelationId, String failureReason) {
        document.setEventCorrelationId(eventCorrelationId);
        document.setEventStatus(EventStatus.FAILED);
        document.setEventStatusChangedAt(Instant.now());
        document.setEventFailureReason(failureReason);
    }
}

