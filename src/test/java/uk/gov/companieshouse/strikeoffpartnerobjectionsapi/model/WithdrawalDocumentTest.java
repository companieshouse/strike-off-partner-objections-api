package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit-test")
class WithdrawalDocumentTest {

    private WithdrawalDocument withdrawalDocument;

    @BeforeEach
    void setUp() {
        withdrawalDocument = new WithdrawalDocument();
    }

    @Test
    void testSetAndGetId() {
        String id = "507f1f77bcf86cd799439011";
        withdrawalDocument.setId(id);
        assertEquals(id, withdrawalDocument.getId());
    }

    @Test
    void testSetAndGetCompanyNumber() {
        String companyNumber = "00000000";
        withdrawalDocument.setCompanyNumber(companyNumber);
        assertEquals(companyNumber, withdrawalDocument.getCompanyNumber());
    }

    @Test
    void testSetAndGetSubmissionCompanyName() {
        String submissionCompanyName = "Test Company Ltd";
        withdrawalDocument.setSubmissionCompanyName(submissionCompanyName);
        assertEquals(submissionCompanyName, withdrawalDocument.getSubmissionCompanyName());
    }

    @Test
    void testSetAndGetWithdrawalId() {
        String withdrawalId = "withdrawal-123";
        withdrawalDocument.setWithdrawalId(withdrawalId);
        assertEquals(withdrawalId, withdrawalDocument.getWithdrawalId());
    }

    @Test
    void testSetAndGetPartnerOrganisation() {
        String partnerOrganisation = "Partner Org";
        withdrawalDocument.setPartnerOrganisation(partnerOrganisation);
        assertEquals(partnerOrganisation, withdrawalDocument.getPartnerOrganisation());
    }

    @Test
    void testSetAndGetPartnerContactEmail() {
        String email = "contact@example.com";
        withdrawalDocument.setPartnerContactEmail(email);
        assertEquals(email, withdrawalDocument.getPartnerContactEmail());
    }

    @Test
    void testSetAndGetPartnerCaseReference() {
        String partnerCaseReference = "CASE-001";
        withdrawalDocument.setPartnerCaseReference(partnerCaseReference);
        assertEquals(partnerCaseReference, withdrawalDocument.getPartnerCaseReference());
    }

    @Test
    void testSetAndGetPartnerObjectionWorkstream() {
        String workstream = "WORKSTREAM-A";
        withdrawalDocument.setPartnerObjectionWorkstream(workstream);
        assertEquals(workstream, withdrawalDocument.getPartnerObjectionWorkstream());
    }

    @Test
    void testSetAndGetProcessingStatus() {
        String status = "PENDING";
        withdrawalDocument.setProcessingStatus(status);
        assertEquals(status, withdrawalDocument.getProcessingStatus());
    }

    @Test
    void testSetAndGetCreatedAt() {
        Instant now = Instant.now();
        withdrawalDocument.setCreatedAt(now);
        assertEquals(now, withdrawalDocument.getCreatedAt());
    }

    @Test
    void testSetAndGetEtag() {
        String etag = "etag-123";
        withdrawalDocument.setEtag(etag);
        assertEquals(etag, withdrawalDocument.getEtag());
    }

    @Test
    void testSetAndGetLinks() {
        PartnerLinks links = new PartnerLinks();
        links.setSelf("http://example.com/withdrawals/123");
        withdrawalDocument.setLinks(links);
        assertEquals(links, withdrawalDocument.getLinks());
    }

    @Test
    void testSetAndGetKind() {
        String kind = "withdrawal";
        withdrawalDocument.setKind(kind);
        assertEquals(kind, withdrawalDocument.getKind());
    }

    @Test
    void testSetAllFieldsAndRetrieve() {
        String id = "507f1f77bcf86cd799439011";
        String companyNumber = "00000000";
        String submissionCompanyName = "Test Company Ltd";
        String withdrawalId = "withdrawal-123";
        String partnerOrganisation = "Partner Org";
        String email = "contact@example.com";
        String partnerCaseReference = "CASE-001";
        String workstream = "WORKSTREAM-A";
        String status = "PENDING";
        Instant now = Instant.now();
        String etag = "etag-123";
        PartnerLinks links = new PartnerLinks();
        links.setSelf("http://example.com/withdrawals/123");
        String kind = "withdrawal";

        withdrawalDocument.setId(id);
        withdrawalDocument.setCompanyNumber(companyNumber);
        withdrawalDocument.setSubmissionCompanyName(submissionCompanyName);
        withdrawalDocument.setWithdrawalId(withdrawalId);
        withdrawalDocument.setPartnerOrganisation(partnerOrganisation);
        withdrawalDocument.setPartnerContactEmail(email);
        withdrawalDocument.setPartnerCaseReference(partnerCaseReference);
        withdrawalDocument.setPartnerObjectionWorkstream(workstream);
        withdrawalDocument.setProcessingStatus(status);
        withdrawalDocument.setCreatedAt(now);
        withdrawalDocument.setEtag(etag);
        withdrawalDocument.setLinks(links);
        withdrawalDocument.setKind(kind);

        assertEquals(id, withdrawalDocument.getId());
        assertEquals(companyNumber, withdrawalDocument.getCompanyNumber());
        assertEquals(submissionCompanyName, withdrawalDocument.getSubmissionCompanyName());
        assertEquals(withdrawalId, withdrawalDocument.getWithdrawalId());
        assertEquals(partnerOrganisation, withdrawalDocument.getPartnerOrganisation());
        assertEquals(email, withdrawalDocument.getPartnerContactEmail());
        assertEquals(partnerCaseReference, withdrawalDocument.getPartnerCaseReference());
        assertEquals(workstream, withdrawalDocument.getPartnerObjectionWorkstream());
        assertEquals(status, withdrawalDocument.getProcessingStatus());
        assertEquals(now, withdrawalDocument.getCreatedAt());
        assertEquals(etag, withdrawalDocument.getEtag());
        assertEquals(links, withdrawalDocument.getLinks());
        assertEquals(kind, withdrawalDocument.getKind());
    }

    @Test
    void testInitialValuesAreNull() {
        assertNull(withdrawalDocument.getId());
        assertNull(withdrawalDocument.getCompanyNumber());
        assertNull(withdrawalDocument.getSubmissionCompanyName());
        assertNull(withdrawalDocument.getWithdrawalId());
        assertNull(withdrawalDocument.getPartnerOrganisation());
        assertNull(withdrawalDocument.getPartnerContactEmail());
        assertNull(withdrawalDocument.getPartnerCaseReference());
        assertNull(withdrawalDocument.getPartnerObjectionWorkstream());
        assertNull(withdrawalDocument.getProcessingStatus());
        assertNull(withdrawalDocument.getCreatedAt());
        assertNull(withdrawalDocument.getEtag());
        assertNull(withdrawalDocument.getLinks());
        assertNull(withdrawalDocument.getKind());
    }

    @Test
    void testSetToNull() {
        withdrawalDocument.setId("some-id");
        withdrawalDocument.setId(null);
        assertNull(withdrawalDocument.getId());
    }

    @Test
    void testUpdateExistingValue() {
        withdrawalDocument.setCompanyNumber("00000000");
        assertEquals("00000000", withdrawalDocument.getCompanyNumber());

        withdrawalDocument.setCompanyNumber("11111111");
        assertEquals("11111111", withdrawalDocument.getCompanyNumber());
    }

    @Test
    void testEmptyStringValues() {
        withdrawalDocument.setCompanyNumber("");
        withdrawalDocument.setPartnerOrganisation("");

        assertEquals("", withdrawalDocument.getCompanyNumber());
        assertEquals("", withdrawalDocument.getPartnerOrganisation());
    }

    @Test
    void testLinksObjectCanBeNull() {
        withdrawalDocument.setLinks(null);
        assertNull(withdrawalDocument.getLinks());
    }

    @Test
    void testMultipleInstantsCanBeDifferent() {
        Instant time1 = Instant.now();
        Instant time2 = Instant.now().plusSeconds(60);

        withdrawalDocument.setCreatedAt(time1);
        assertEquals(time1, withdrawalDocument.getCreatedAt());

        withdrawalDocument.setCreatedAt(time2);
        assertEquals(time2, withdrawalDocument.getCreatedAt());
    }
}

