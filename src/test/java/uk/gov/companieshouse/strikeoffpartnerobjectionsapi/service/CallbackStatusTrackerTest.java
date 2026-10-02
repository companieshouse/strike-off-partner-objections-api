package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit-test")
class CallbackStatusTrackerTest {

    @Test
    void testMarkCallbackSuccess() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "test-correlation-id-123";
        Instant timestamp = Instant.now();

        CallbackStatusTracker.markCallbackSuccess(document, correlationId, timestamp);

        assertEquals(CallbackStatus.SUCCESS, document.getCallbackStatus());
        assertEquals(correlationId, document.getCallbackCorrelationId());
        assertEquals(timestamp, document.getCallbackStatusChangedAt());
        assertNull(document.getCallbackFailureReason());
    }

    @Test
    void testMarkCallbackSuccess_setsTimestamp() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "correlation-id-456";
        Instant timestamp = Instant.now();

        CallbackStatusTracker.markCallbackSuccess(document, correlationId, timestamp);

        assertNotNull(document.getCallbackStatusChangedAt());
        assertEquals(timestamp, document.getCallbackStatusChangedAt());
    }

    @Test
    void testMarkCallbackFailed() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "failed-correlation-id-789";
        String failureReason = "Connection timeout";
        Instant timestamp = Instant.now();

        CallbackStatusTracker.markCallbackFailed(document, correlationId, failureReason, timestamp);

        assertEquals(CallbackStatus.FAILED, document.getCallbackStatus());
        assertEquals(correlationId, document.getCallbackCorrelationId());
        assertEquals(failureReason, document.getCallbackFailureReason());
        assertEquals(timestamp, document.getCallbackStatusChangedAt());
    }

    @Test
    void testMarkCallbackFailed_with_nullReason() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "correlation-id-null-reason";
        Instant timestamp = Instant.now();

        CallbackStatusTracker.markCallbackFailed(document, correlationId, null, timestamp);

        assertEquals(CallbackStatus.FAILED, document.getCallbackStatus());
        assertEquals(correlationId, document.getCallbackCorrelationId());
        assertNull(document.getCallbackFailureReason());
        assertEquals(timestamp, document.getCallbackStatusChangedAt());
    }

    @Test
    void testMarkCallbackFailed_setsTimestamp() {
        ObjectionDocument document = new ObjectionDocument();
        Instant timestamp = Instant.now();

        CallbackStatusTracker.markCallbackFailed(document, "test-id", "Test failure", timestamp);

        assertNotNull(document.getCallbackStatusChangedAt());
        assertEquals(timestamp, document.getCallbackStatusChangedAt());
    }

    @Test
    void testMarkCallbackFailed_with_emptyFailureReason() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "empty-reason-test";
        String emptyReason = "";
        Instant timestamp = Instant.now();

        CallbackStatusTracker.markCallbackFailed(document, correlationId, emptyReason, timestamp);

        assertEquals(emptyReason, document.getCallbackFailureReason());
    }
}

