package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit-test")
class ServiceExceptionTest {

    @Test
    void constructor_whenInvoked_setsMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");

        ServiceException exception = new ServiceException("service failure", cause);

        assertEquals("service failure", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void serviceException_whenCreated_isRuntimeException() {
        ServiceException exception = new ServiceException("service failure", new RuntimeException("cause"));

        assertTrue(exception instanceof RuntimeException);
    }
}
