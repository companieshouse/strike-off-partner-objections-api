package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

@Tag("unit-test")
class ObjectionRequestMapperTest {

    private final ObjectionRequestMapper mapper = new ObjectionRequestMapperImpl();

    private static final String COMPANY_NUMBER = "01234567";
    private static final String PARTNER_ORG = "hmrc";
    private static final String OBJECTION_ID = "obj-abc-123";
    private static final String ETAG = "etag-xyz";

    @Test
    void toObjectionDocumentMapsRequestFieldsCorrectly() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setSubmissionCompanyName("Acme Ltd");
        request.setPartnerCaseReference("CASE-001");
        request.setPartnerContactEmail("test@example.com");
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
        request.setPartnerObjectionReason(PartnerObjectionReason.OTHER);

        ObjectionDocument doc = mapper.toObjectionDocument(
                request, COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID, ETAG);

        assertThat(doc.getSubmissionCompanyName()).isEqualTo("Acme Ltd");
        assertThat(doc.getPartnerCaseReference()).isEqualTo("CASE-001");
        assertThat(doc.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(doc.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(doc.getPartnerOrganisation()).isEqualTo(PARTNER_ORG);
        assertThat(doc.getObjectionId()).isEqualTo(OBJECTION_ID);
        assertThat(doc.getEtag()).isEqualTo(ETAG);
    }

    @Test
    void toObjectionDocumentSetsProcessingStatusToObjectionSubmitted() {
        ObjectionDocument doc = mapper.toObjectionDocument(
                new CreateObjectionRequest(), COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID, ETAG);

        assertThat(doc.getProcessingStatus())
                .isEqualTo(ObjectionProcessingStatus.OBJECTION_SUBMITTED.getValue());
    }

    @Test
    void toObjectionDocumentSetsKind() {
        ObjectionDocument doc = mapper.toObjectionDocument(
                new CreateObjectionRequest(), COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID, ETAG);

        assertThat(doc.getKind()).isEqualTo("strike-off-partner-objection#objection");
    }

    @Test
    void toObjectionDocumentBuildsLinksCorrectly() {
        ObjectionDocument doc = mapper.toObjectionDocument(
                new CreateObjectionRequest(), COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID, ETAG);

        assertThat(doc.getLinks()).isNotNull();
        assertThat(doc.getLinks().getSelf())
                .isEqualTo("/company/01234567/strike-off-partner-objections/obj-abc-123");
        assertThat(doc.getLinks().getCompanyProfile())
                .isEqualTo("/company/01234567");
    }

    @Test
    void toObjectionDocumentMapsWorkstreamAndReasonAsString() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
        request.setPartnerObjectionReason(PartnerObjectionReason.OTHER);

        ObjectionDocument doc = mapper.toObjectionDocument(
                request, COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID, ETAG);

        assertThat(doc.getPartnerObjectionWorkstream()).isNotBlank();
        assertThat(doc.getPartnerObjectionReason()).isNotBlank();
    }

    @Test
    void toObjectionDocumentWhenNullRequestReturnsNull() {
        ObjectionDocument doc = mapper.toObjectionDocument(
                null, null, null, null, null);

        assertThat(doc).isNull();
    }
}