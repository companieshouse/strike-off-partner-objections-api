package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit-test")
class HmrcCallbackPayloadTest {

    private HmrcCallbackPayload payload;

    @BeforeEach
    void setUp() {
        payload = new HmrcCallbackPayload();
    }

    @Test
    void testConstructorWithAllParameters() {
        String resourceId = "objection-123";
        String companyNumber = "12345678";
        String resourceUri = "/company/12345678/objections/objection-123";

        HmrcCallbackPayload result = new HmrcCallbackPayload(
                CallbackResourceKind.OBJECTION,
                resourceId,
                companyNumber,
                resourceUri
        );

        assertEquals(CallbackResourceKind.OBJECTION, result.getResourceKind());
        assertEquals(resourceId, result.getResourceId());
        assertEquals(companyNumber, result.getCompanyNumber());
        assertEquals(resourceUri, result.getResourceUri());
    }

    @Test
    void testConstructorWithWithdrawalResourceKind() {
        HmrcCallbackPayload result = new HmrcCallbackPayload(
                CallbackResourceKind.WITHDRAWAL,
                "withdrawal-456",
                "87654321",
                "/company/87654321/withdrawals/withdrawal-456"
        );

        assertEquals(CallbackResourceKind.WITHDRAWAL, result.getResourceKind());
        assertEquals("withdrawal-456", result.getResourceId());
    }

    @Test
    void testDefaultConstructor() {
        HmrcCallbackPayload newPayload = new HmrcCallbackPayload();
        assertNull(newPayload.getResourceKind());
        assertNull(newPayload.getResourceId());
        assertNull(newPayload.getCompanyNumber());
        assertNull(newPayload.getResourceUri());
    }

    @Test
    void testSetAndGetResourceKind() {
        payload.setResourceKind(CallbackResourceKind.OBJECTION);
        assertEquals(CallbackResourceKind.OBJECTION, payload.getResourceKind());

        payload.setResourceKind(CallbackResourceKind.WITHDRAWAL);
        assertEquals(CallbackResourceKind.WITHDRAWAL, payload.getResourceKind());
    }

    @Test
    void testSetAndGetResourceId() {
        String resourceId = "res-123";
        payload.setResourceId(resourceId);
        assertEquals(resourceId, payload.getResourceId());
    }

    @Test
    void testSetAndGetCompanyNumber() {
        String companyNumber = "99999999";
        payload.setCompanyNumber(companyNumber);
        assertEquals(companyNumber, payload.getCompanyNumber());
    }

    @Test
    void testSetAndGetResourceUri() {
        String uri = "/company/12345678/objections/obj-123";
        payload.setResourceUri(uri);
        assertEquals(uri, payload.getResourceUri());
    }

    @Test
    void testSetNullResourceKind() {
        payload.setResourceKind(null);
        assertNull(payload.getResourceKind());
    }

    @Test
    void testSetNullResourceId() {
        payload.setResourceId(null);
        assertNull(payload.getResourceId());
    }

    @Test
    void testSetNullCompanyNumber() {
        payload.setCompanyNumber(null);
        assertNull(payload.getCompanyNumber());
    }

    @Test
    void testSetNullResourceUri() {
        payload.setResourceUri(null);
        assertNull(payload.getResourceUri());
    }

    @Test
    void testMultipleSetters() {
        payload.setResourceKind(CallbackResourceKind.OBJECTION);
        payload.setResourceId("obj-789");
        payload.setCompanyNumber("11111111");
        payload.setResourceUri("/test/uri");

        assertEquals(CallbackResourceKind.OBJECTION, payload.getResourceKind());
        assertEquals("obj-789", payload.getResourceId());
        assertEquals("11111111", payload.getCompanyNumber());
        assertEquals("/test/uri", payload.getResourceUri());
    }

    @Test
    void testUpdateExistingValues() {
        payload.setResourceId("initial-id");
        assertEquals("initial-id", payload.getResourceId());

        payload.setResourceId("updated-id");
        assertEquals("updated-id", payload.getResourceId());
    }

    @Test
    void testConstructorDoesNotModifyAfterCreation() {
        HmrcCallbackPayload payload1 = new HmrcCallbackPayload(
                CallbackResourceKind.OBJECTION,
                "id-1",
                "11111111",
                "/uri/1"
        );

        HmrcCallbackPayload payload2 = new HmrcCallbackPayload(
                CallbackResourceKind.WITHDRAWAL,
                "id-2",
                "22222222",
                "/uri/2"
        );

        assertEquals("id-1", payload1.getResourceId());
        assertEquals("id-2", payload2.getResourceId());
    }

    @Test
    void testEmptyStringValues() {
        payload.setResourceId("");
        payload.setCompanyNumber("");
        payload.setResourceUri("");

        assertEquals("", payload.getResourceId());
        assertEquals("", payload.getCompanyNumber());
        assertEquals("", payload.getResourceUri());
    }
}

