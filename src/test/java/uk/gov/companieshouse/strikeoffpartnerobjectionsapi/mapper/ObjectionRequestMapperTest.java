package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

@Tag("unit-test")
class ObjectionRequestMapperTest {

    private final ObjectionRequestMapper mapper = new ObjectionRequestMapperImpl();

    private static final String COMPANY_NUMBER = "01234567";
    private static final String PARTNER_ORG = "hmrc";
    private static final String OBJECTION_ID = "obj-abc-123";
    private static final String DEBT_MANAGEMENT_WORKSTREAM = "debt-management";

    @Test
    void toObjectionDocument_whenRequestFieldsAreProvided_mapsRequestFieldsCorrectly() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setSubmissionCompanyName("Acme Ltd");
        request.setPartnerCaseReference("CASE-001");
        request.setPartnerContactEmail("test@example.com");
        request.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
        request.setPartnerObjectionReason(PartnerObjectionReason.OTHER);

        ObjectionDocument doc = mapper.toObjectionDocument(
                request, COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID);

        assertThat(doc.getSubmissionCompanyName()).isEqualTo("Acme Ltd");
        assertThat(doc.getPartnerCaseReference()).isEqualTo("CASE-001");
        assertThat(doc.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(doc.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(doc.getPartnerOrganisation()).isEqualTo(PARTNER_ORG);
        assertThat(doc.getObjectionId()).isEqualTo(OBJECTION_ID);
        assertThat(doc.getEtag()).isNotNull();
    }

    @Test
    void toObjectionDocument_whenDocumentIsCreated_setsProcessingStatusToObjectionSubmitted() {
        ObjectionDocument doc = mapper.toObjectionDocument(
                new CreateObjectionRequest(), COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID);

        assertThat(doc.getProcessingStatus())
                .isEqualTo(ObjectionProcessingStatus.OBJECTION_SUBMITTED.getValue());
    }

    @Test
    void toObjectionDocument_whenDocumentIsCreated_setsKind() {
        ObjectionDocument doc = mapper.toObjectionDocument(
                new CreateObjectionRequest(), COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID);

        assertThat(doc.getKind()).isEqualTo("strike-off-partner-objection#objection");
    }

    @Test
    void toObjectionDocument_whenCompanyAndObjectionIdsAreProvided_buildsLinksCorrectly() {
        ObjectionDocument doc = mapper.toObjectionDocument(
                new CreateObjectionRequest(), COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID);

        assertThat(doc.getLinks()).isNotNull();
        assertThat(doc.getLinks().getSelf())
                .isEqualTo("/company/01234567/strike-off-partner-objections/obj-abc-123");
        assertThat(doc.getLinks().getCompanyProfile())
                .isEqualTo("/company/01234567");
    }

    @Test
    void toObjectionDocument_whenWorkstreamAndReasonAreProvided_mapsWorkstreamAndReasonAsString() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
        request.setPartnerObjectionReason(PartnerObjectionReason.OTHER);

        ObjectionDocument doc = mapper.toObjectionDocument(
                request, COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID);

        assertThat(doc.getPartnerObjectionWorkstream()).isNotBlank();
        assertThat(doc.getPartnerObjectionReason()).isNotBlank();
    }

    @Test
    void toObjectionDocument_whenAllParametersAreNull_returnsNull() {
        ObjectionDocument doc = mapper.toObjectionDocument(
                null, null, null, null);

        assertThat(doc).isNull();
    }

    @Test
    void toObjectionDocument_whenRequestIsNullAndMetadataIsProvided_stillBuildsDocument() {
        ObjectionDocument doc = mapper.toObjectionDocument(
                null, COMPANY_NUMBER, PARTNER_ORG, OBJECTION_ID);

        assertThat(doc).isNotNull();
        assertThat(doc.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(doc.getPartnerOrganisation()).isEqualTo(PARTNER_ORG);
        assertThat(doc.getObjectionId()).isEqualTo(OBJECTION_ID);
        assertThat(doc.getEtag()).isNotNull();
        assertThat(doc.getSubmissionCompanyName()).isNull();
        assertThat(doc.getPartnerCaseReference()).isNull();
        assertThat(doc.getPartnerObjectionWorkstream()).isNull();
        assertThat(doc.getPartnerObjectionReason()).isNull();
        assertThat(doc.getPartnerContactEmail()).isNull();
    }
}