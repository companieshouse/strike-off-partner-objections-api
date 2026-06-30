package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit-test")
class ObjectionDocumentTest {

    private ObjectionDocument objectionDocument;

    @BeforeEach
    void setUp() {
        objectionDocument = new ObjectionDocument();
    }

    @Test
    void testSetAndGetId() {
        String id = "507f1f77bcf86cd799439011";
        objectionDocument.setId(id);
        assertEquals(id, objectionDocument.getId());
    }

    @Test
    void testSetAndGetCompanyNumber() {
        String companyNumber = "00000000";
        objectionDocument.setCompanyNumber(companyNumber);
        assertEquals(companyNumber, objectionDocument.getCompanyNumber());
    }

    @Test
    void testSetAndGetPartnerOrganisation() {
        String partnerOrganisation = "Partner Org";
        objectionDocument.setPartnerOrganisation(partnerOrganisation);
        assertEquals(partnerOrganisation, objectionDocument.getPartnerOrganisation());
    }

    @Test
    void testSetAndGetSubmissionCompanyName() {
        String submissionCompanyName = "Test Company Ltd";
        objectionDocument.setSubmissionCompanyName(submissionCompanyName);
        assertEquals(submissionCompanyName, objectionDocument.getSubmissionCompanyName());
    }

    @Test
    void testSetAndGetPartnerCaseReference() {
        String partnerCaseReference = "CASE-001";
        objectionDocument.setPartnerCaseReference(partnerCaseReference);
        assertEquals(partnerCaseReference, objectionDocument.getPartnerCaseReference());
    }

    @Test
    void testSetAndGetPartnerObjectionWorkstream() {
        String workstream = "WORKSTREAM-A";
        objectionDocument.setPartnerObjectionWorkstream(workstream);
        assertEquals(workstream, objectionDocument.getPartnerObjectionWorkstream());
    }

    @Test
    void testSetAndGetPartnerObjectionReason() {
        String reason = "Test Reason";
        objectionDocument.setPartnerObjectionReason(reason);
        assertEquals(reason, objectionDocument.getPartnerObjectionReason());
    }

    @Test
    void testSetAndGetPartnerContactEmail() {
        String email = "contact@example.com";
        objectionDocument.setPartnerContactEmail(email);
        assertEquals(email, objectionDocument.getPartnerContactEmail());
    }

    @Test
    void testSetAndGetObjectionId() {
        String objectionId = "objection-123";
        objectionDocument.setObjectionId(objectionId);
        assertEquals(objectionId, objectionDocument.getObjectionId());
    }

    @Test
    void testSetAndGetProcessingStatus() {
        String status = "PENDING";
        objectionDocument.setProcessingStatus(status);
        assertEquals(status, objectionDocument.getProcessingStatus());
    }

    @Test
    void testSetAndGetCreatedAt() {
        Instant now = Instant.now();
        objectionDocument.setCreatedAt(now);
        assertEquals(now, objectionDocument.getCreatedAt());
    }

    @Test
    void testSetAndGetProcessingStatusChangedAt() {
        Instant now = Instant.now();
        objectionDocument.setProcessingStatusChangedAt(now);
        assertEquals(now, objectionDocument.getProcessingStatusChangedAt());
    }

    @Test
    void testSetAndGetInitialExpirationOn() {
        Instant futureTime = Instant.now().plusSeconds(86400);
        objectionDocument.setInitialExpirationOn(futureTime);
        assertEquals(futureTime, objectionDocument.getInitialExpirationOn());
    }

    @Test
    void testSetAndGetFailureReason() {
        String failureReason = "Test failure";
        objectionDocument.setFailureReason(failureReason);
        assertEquals(failureReason, objectionDocument.getFailureReason());
    }

    @Test
    void testSetAndGetEventStatus() {
        EventStatus status = EventStatus.PENDING;
        objectionDocument.setEventStatus(status);
        assertEquals(status, objectionDocument.getEventStatus());
    }

    @Test
    void testSetAndGetEventStatusChangedAt() {
        Instant now = Instant.now();
        objectionDocument.setEventStatusChangedAt(now);
        assertEquals(now, objectionDocument.getEventStatusChangedAt());
    }

    @Test
    void testSetAndGetEventCorrelationId() {
        String eventCorrelationId = "corr-id-123";
        objectionDocument.setEventCorrelationId(eventCorrelationId);
        assertEquals(eventCorrelationId, objectionDocument.getEventCorrelationId());
    }

    @Test
    void testSetAndGetEventFailureReason() {
        String eventFailureReason = "Kafka publish failed";
        objectionDocument.setEventFailureReason(eventFailureReason);
        assertEquals(eventFailureReason, objectionDocument.getEventFailureReason());
    }

    @Test
    void testSetAndGetEtag() {
        String etag = "etag-123";
        objectionDocument.setEtag(etag);
        assertEquals(etag, objectionDocument.getEtag());
    }

    @Test
    void testSetAndGetLinks() {
        PartnerLinks links = new PartnerLinks();
        links.setSelf("http://example.com/objections/123");
        objectionDocument.setLinks(links);
        assertEquals(links, objectionDocument.getLinks());
    }

    @Test
    void testSetAndGetKind() {
        String kind = "objection";
        objectionDocument.setKind(kind);
        assertEquals(kind, objectionDocument.getKind());
    }

    @Test
    void testSetAllFieldsAndRetrieve() {
        String id = "507f1f77bcf86cd799439011";
        String companyNumber = "00000000";
        String partnerOrganisation = "Partner Org";
        String submissionCompanyName = "Test Company Ltd";
        String partnerCaseReference = "CASE-001";
        String workstream = "WORKSTREAM-A";
        String reason = "Test Reason";
        String email = "contact@example.com";
        String objectionId = "objection-123";
        String status = "PENDING";
        Instant now = Instant.now();
        Instant futureTime = Instant.now().plusSeconds(86400);
        String failureReason = "Test failure";
        EventStatus eventStatus = EventStatus.PENDING;
        String eventCorrelationId = "corr-id-123";
        String eventFailureReason = "Kafka publish failed";
        String etag = "etag-123";
        PartnerLinks links = new PartnerLinks();
        links.setSelf("http://example.com/objections/123");
        String kind = "objection";

        objectionDocument.setId(id);
        objectionDocument.setCompanyNumber(companyNumber);
        objectionDocument.setPartnerOrganisation(partnerOrganisation);
        objectionDocument.setSubmissionCompanyName(submissionCompanyName);
        objectionDocument.setPartnerCaseReference(partnerCaseReference);
        objectionDocument.setPartnerObjectionWorkstream(workstream);
        objectionDocument.setPartnerObjectionReason(reason);
        objectionDocument.setPartnerContactEmail(email);
        objectionDocument.setObjectionId(objectionId);
        objectionDocument.setProcessingStatus(status);
        objectionDocument.setCreatedAt(now);
        objectionDocument.setProcessingStatusChangedAt(now);
        objectionDocument.setInitialExpirationOn(futureTime);
        objectionDocument.setFailureReason(failureReason);
        objectionDocument.setEventStatus(eventStatus);
        objectionDocument.setEventStatusChangedAt(now);
        objectionDocument.setEventCorrelationId(eventCorrelationId);
        objectionDocument.setEventFailureReason(eventFailureReason);
        objectionDocument.setEtag(etag);
        objectionDocument.setLinks(links);
        objectionDocument.setKind(kind);

        assertEquals(id, objectionDocument.getId());
        assertEquals(companyNumber, objectionDocument.getCompanyNumber());
        assertEquals(partnerOrganisation, objectionDocument.getPartnerOrganisation());
        assertEquals(submissionCompanyName, objectionDocument.getSubmissionCompanyName());
        assertEquals(partnerCaseReference, objectionDocument.getPartnerCaseReference());
        assertEquals(workstream, objectionDocument.getPartnerObjectionWorkstream());
        assertEquals(reason, objectionDocument.getPartnerObjectionReason());
        assertEquals(email, objectionDocument.getPartnerContactEmail());
        assertEquals(objectionId, objectionDocument.getObjectionId());
        assertEquals(status, objectionDocument.getProcessingStatus());
        assertEquals(now, objectionDocument.getCreatedAt());
        assertEquals(now, objectionDocument.getProcessingStatusChangedAt());
        assertEquals(futureTime, objectionDocument.getInitialExpirationOn());
        assertEquals(failureReason, objectionDocument.getFailureReason());
        assertEquals(eventStatus, objectionDocument.getEventStatus());
        assertEquals(now, objectionDocument.getEventStatusChangedAt());
        assertEquals(eventCorrelationId, objectionDocument.getEventCorrelationId());
        assertEquals(eventFailureReason, objectionDocument.getEventFailureReason());
        assertEquals(etag, objectionDocument.getEtag());
        assertEquals(links, objectionDocument.getLinks());
        assertEquals(kind, objectionDocument.getKind());
    }

    @Test
    void testInitialValuesAreNull() {
        assertNull(objectionDocument.getId());
        assertNull(objectionDocument.getCompanyNumber());
        assertNull(objectionDocument.getPartnerOrganisation());
        assertNull(objectionDocument.getSubmissionCompanyName());
        assertNull(objectionDocument.getPartnerCaseReference());
        assertNull(objectionDocument.getPartnerObjectionWorkstream());
        assertNull(objectionDocument.getPartnerObjectionReason());
        assertNull(objectionDocument.getPartnerContactEmail());
        assertNull(objectionDocument.getObjectionId());
        assertNull(objectionDocument.getProcessingStatus());
        assertNull(objectionDocument.getCreatedAt());
        assertNull(objectionDocument.getProcessingStatusChangedAt());
        assertNull(objectionDocument.getInitialExpirationOn());
        assertNull(objectionDocument.getFailureReason());
        assertNull(objectionDocument.getEventStatus());
        assertNull(objectionDocument.getEventStatusChangedAt());
        assertNull(objectionDocument.getEventCorrelationId());
        assertNull(objectionDocument.getEventFailureReason());
        assertNull(objectionDocument.getEtag());
        assertNull(objectionDocument.getLinks());
        assertNull(objectionDocument.getKind());
    }

    @Test
    void testSetToNull() {
        objectionDocument.setId("some-id");
        objectionDocument.setId(null);
        assertNull(objectionDocument.getId());
    }

    @Test
    void testUpdateExistingValue() {
        objectionDocument.setCompanyNumber("00000000");
        assertEquals("00000000", objectionDocument.getCompanyNumber());

        objectionDocument.setCompanyNumber("11111111");
        assertEquals("11111111", objectionDocument.getCompanyNumber());
    }

    @Test
    void testEmptyStringValues() {
        objectionDocument.setCompanyNumber("");
        objectionDocument.setPartnerOrganisation("");

        assertEquals("", objectionDocument.getCompanyNumber());
        assertEquals("", objectionDocument.getPartnerOrganisation());
    }
}

