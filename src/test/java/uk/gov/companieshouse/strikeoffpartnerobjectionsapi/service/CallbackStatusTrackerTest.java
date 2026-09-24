package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit-test")
class CallbackStatusTrackerTest {

    @Test
    void testMarkCallbackSuccess() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "test-correlation-id-123";

        CallbackStatusTracker.markCallbackSuccess(document, correlationId);

        assertEquals(CallbackStatus.SUCCESS, document.getCallbackStatus());
        assertEquals(correlationId, document.getCallbackCorrelationId());
        assertNotNull(document.getCallbackStatusChangedAt());
        assertNull(document.getCallbackFailureReason());
    }

    @Test
    void testMarkCallbackSuccess_setsTimestamp() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "correlation-id-456";
        Instant beforeCall = Instant.now();

        CallbackStatusTracker.markCallbackSuccess(document, correlationId);

        Instant afterCall = Instant.now();
        assertNotNull(document.getCallbackStatusChangedAt());
        assertTrue(document.getCallbackStatusChangedAt().isAfter(beforeCall.minusSeconds(1)));
        assertTrue(document.getCallbackStatusChangedAt().isBefore(afterCall.plusSeconds(1)));
    }

    @Test
    void testMarkCallbackFailed() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "failed-correlation-id-789";
        String failureReason = "Connection timeout";

        CallbackStatusTracker.markCallbackFailed(document, correlationId, failureReason);

        assertEquals(CallbackStatus.FAILED, document.getCallbackStatus());
        assertEquals(correlationId, document.getCallbackCorrelationId());
        assertEquals(failureReason, document.getCallbackFailureReason());
        assertNotNull(document.getCallbackStatusChangedAt());
    }

    @Test
    void testMarkCallbackFailed_with_nullReason() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "correlation-id-null-reason";

        CallbackStatusTracker.markCallbackFailed(document, correlationId, null);

        assertEquals(CallbackStatus.FAILED, document.getCallbackStatus());
        assertEquals(correlationId, document.getCallbackCorrelationId());
        assertNull(document.getCallbackFailureReason());
    }

    @Test
    void testMarkCallbackFailed_setsTimestamp() {
        ObjectionDocument document = new ObjectionDocument();
        Instant beforeCall = Instant.now();

        CallbackStatusTracker.markCallbackFailed(document, "test-id", "Test failure");

        Instant afterCall = Instant.now();
        assertNotNull(document.getCallbackStatusChangedAt());
        assertTrue(document.getCallbackStatusChangedAt().isAfter(beforeCall.minusSeconds(1)));
        assertTrue(document.getCallbackStatusChangedAt().isBefore(afterCall.plusSeconds(1)));
    }

    @Test
    void testMarkCallbackFailed_with_emptyFailureReason() {
        ObjectionDocument document = new ObjectionDocument();
        String correlationId = "empty-reason-test";
        String emptyReason = "";

        CallbackStatusTracker.markCallbackFailed(document, correlationId, emptyReason);

        assertEquals(emptyReason, document.getCallbackFailureReason());
    }
}

