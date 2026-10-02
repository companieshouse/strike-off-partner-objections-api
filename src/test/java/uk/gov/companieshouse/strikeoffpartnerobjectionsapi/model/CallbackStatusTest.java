package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("unit-test")
class CallbackStatusTest {

    @Test
    void testEnumValuesExist() {
        assertNotNull(CallbackStatus.SUCCESS);
        assertNotNull(CallbackStatus.FAILED);
    }

    @Test
    void testEnumValues() {
        assertEquals("SUCCESS", CallbackStatus.SUCCESS.name());
        assertEquals("FAILED", CallbackStatus.FAILED.name());
    }

    @Test
    void testEnumValueOf() {
        assertEquals(CallbackStatus.SUCCESS, CallbackStatus.valueOf("SUCCESS"));
        assertEquals(CallbackStatus.FAILED, CallbackStatus.valueOf("FAILED"));
    }

    @Test
    void testEnumValues_returnsAllValues() {
        CallbackStatus[] values = CallbackStatus.values();
        assertEquals(2, values.length);
    }
}

