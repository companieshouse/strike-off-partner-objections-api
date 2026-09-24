package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit-test")
class PartnerRequestDocumentTest {

    private ObjectionDocument document;

    @BeforeEach
    void setUp() {
        document = new ObjectionDocument();
    }

    @Test
    void testSetAndGetId() {
        String id = "test-id-123";
        document.setId(id);
        assertEquals(id, document.getId());
    }

    @Test
    void testSetAndGetSubmissionCompanyName() {
        String companyName = "Test Company Ltd";
        document.setSubmissionCompanyName(companyName);
        assertEquals(companyName, document.getSubmissionCompanyName());
    }

    @Test
    void testSetAndGetPartnerOrganisation() {
        String partnerOrg = "Partner Org Ltd";
        document.setPartnerOrganisation(partnerOrg);
        assertEquals(partnerOrg, document.getPartnerOrganisation());
    }

    @Test
    void testSetAndGetPartnerContactEmail() {
        String email = "contact@partner.com";
        document.setPartnerContactEmail(email);
        assertEquals(email, document.getPartnerContactEmail());
    }

    @Test
    void testSetAndGetPartnerCaseReference() {
        String caseRef = "CASE-2024-001";
        document.setPartnerCaseReference(caseRef);
        assertEquals(caseRef, document.getPartnerCaseReference());
    }

    @Test
    void testSetAndGetPartnerObjectionWorkstream() {
        String workstream = "Workstream A";
        document.setPartnerObjectionWorkstream(workstream);
        assertEquals(workstream, document.getPartnerObjectionWorkstream());
    }

    @Test
    void testSetAndGetCreatedAt() {
        Instant now = Instant.now();
        document.setCreatedAt(now);
        assertEquals(now, document.getCreatedAt());
    }

    @Test
    void testSetAndGetEtag() {
        String etag = "etag-123";
        document.setEtag(etag);
        assertEquals(etag, document.getEtag());
    }

    @Test
    void testSetAndGetLinks() {
        PartnerLinks links = new PartnerLinks();
        document.setLinks(links);
        assertEquals(links, document.getLinks());
    }

    @Test
    void testSetAndGetKind() {
        String kind = "objection";
        document.setKind(kind);
        assertEquals(kind, document.getKind());
    }

    @Test
    void testSetAndGetEventStatus() {
        document.setEventStatus(EventStatus.PENDING);
        assertEquals(EventStatus.PENDING, document.getEventStatus());
    }

    @Test
    void testSetAndGetEventStatusChangedAt() {
        Instant timestamp = Instant.now();
        document.setEventStatusChangedAt(timestamp);
        assertEquals(timestamp, document.getEventStatusChangedAt());
    }

    @Test
    void testSetAndGetEventCorrelationId() {
        String eventCorrelationId = "event-123";
        document.setEventCorrelationId(eventCorrelationId);
        assertEquals(eventCorrelationId, document.getEventCorrelationId());
    }

    @Test
    void testSetAndGetEventFailureReason() {
        String failureReason = "Connection timeout";
        document.setEventFailureReason(failureReason);
        assertEquals(failureReason, document.getEventFailureReason());
    }

    @Test
    void testSetAndGetCallbackStatus() {
        document.setCallbackStatus(CallbackStatus.SUCCESS);
        assertEquals(CallbackStatus.SUCCESS, document.getCallbackStatus());
    }

    @Test
    void testSetAndGetCallbackStatusChangedAt() {
        Instant timestamp = Instant.now();
        document.setCallbackStatusChangedAt(timestamp);
        assertEquals(timestamp, document.getCallbackStatusChangedAt());
    }

    @Test
    void testSetAndGetCallbackCorrelationId() {
        String callbackCorrelationId = "callback-123";
        document.setCallbackCorrelationId(callbackCorrelationId);
        assertEquals(callbackCorrelationId, document.getCallbackCorrelationId());
    }

    @Test
    void testSetAndGetCallbackFailureReason() {
        String failureReason = "HMRC service unavailable";
        document.setCallbackFailureReason(failureReason);
        assertEquals(failureReason, document.getCallbackFailureReason());
    }

    @Test
    void testDefaultValues() {
        ObjectionDocument newDoc = new ObjectionDocument();
        assertNull(newDoc.getId());
        assertNull(newDoc.getSubmissionCompanyName());
        assertNull(newDoc.getPartnerOrganisation());
        assertNull(newDoc.getCallbackStatus());
    }

    @Test
    void testMultipleUpdates() {
        document.setId("id-1");
        document.setSubmissionCompanyName("Company 1");
        assertEquals("id-1", document.getId());
        assertEquals("Company 1", document.getSubmissionCompanyName());

        document.setId("id-2");
        document.setSubmissionCompanyName("Company 2");
        assertEquals("id-2", document.getId());
        assertEquals("Company 2", document.getSubmissionCompanyName());
    }

    @Test
    void testNullValues() {
        document.setSubmissionCompanyName("Test");
        document.setSubmissionCompanyName(null);
        assertNull(document.getSubmissionCompanyName());
    }
}

