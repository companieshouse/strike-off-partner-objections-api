package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawalRequestedStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalLinks;

@Tag("unit-test")
class WithdrawalMapperTest {

    private final WithdrawalMapper mapper = new WithdrawalMapperImpl();

    private static final String COMPANY_NUMBER = "12345678";
    private static final String PARTNER_ORG = "hmrc";
    private static final String WITHDRAWAL_ID = "wdl-abc-123";
    private static final String ETAG = "etag-xyz";

    // -----------------------------------------------------------------------
    // toWithdrawalDocument – request → document
    // -----------------------------------------------------------------------

    @Test
    void toWithdrawalDocument_mapsAllRequestFields_whenRequestAndMetadataAreProvided() {
        WithdrawAllObjectionsRequest request = buildRequest();

        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                request, COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(doc.getPartnerOrganisation()).isEqualTo(PARTNER_ORG);
        assertThat(doc.getWithdrawalId()).isEqualTo(WITHDRAWAL_ID);
        assertThat(doc.getEtag()).isEqualTo(ETAG);
        assertThat(doc.getSubmissionCompanyName()).isEqualTo("ACME LTD");
        assertThat(doc.getPartnerCaseReference()).isEqualTo("CASE-001");
        assertThat(doc.getPartnerContactEmail()).isEqualTo("owner@example.com");
    }

    @Test
    void toWithdrawalDocument_convertsWorkstreamEnumToString_whenWorkstreamIsSet() {
        WithdrawAllObjectionsRequest request = buildRequest();
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT);

        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                request, COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc.getPartnerObjectionWorkstream())
                .isEqualTo(PartnerObjectionWorkstream.DEBT_MANAGEMENT.getValue());
    }

    @Test
    void toWithdrawalDocument_setsWithdrawalRequestedProcessingStatus_whenDocumentIsCreated() {
        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                new WithdrawAllObjectionsRequest(), COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc.getProcessingStatus())
                .isEqualTo(WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED.getValue());
    }

    @Test
    void toWithdrawalDocument_setsWithdrawalKind_whenDocumentIsCreated() {
        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                new WithdrawAllObjectionsRequest(), COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc.getKind()).isEqualTo("strike-off-partner-objection#withdrawal");
    }

    @Test
    void toWithdrawalDocument_buildsLinksCorrectly_whenCompanyNumberAndWithdrawalIdAreProvided() {
        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                new WithdrawAllObjectionsRequest(), COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc.getLinks()).isNotNull();
        assertThat(doc.getLinks().getSelf())
                .isEqualTo("/company/12345678/strike-off-partner-objections-withdrawals/wdl-abc-123");
        assertThat(doc.getLinks().getCompanyProfile())
                .isEqualTo("/company/12345678");
    }

    @Test
    void toWithdrawalDocument_doesNotPopulateIdAndCreatedAt_whenMappingIsPerformed() {
        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                new WithdrawAllObjectionsRequest(), COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc.getId()).isNull();
        assertThat(doc.getCreatedAt()).isNull();
    }

    @Test
    void toWithdrawalDocument_mapsWorkstreamToNull_whenWorkstreamIsNull() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        request.setPartnerObjectionWorkstream(null);

        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                request, COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc.getPartnerObjectionWorkstream()).isNull();
    }

    @Test
    void toWithdrawalDocument_returnsNull_whenAllParametersAreNull() {
        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                null, null, null, null, null);

        assertThat(doc).isNull();
    }

    @Test
    void toWithdrawalDocument_mapsMetadataFields_whenRequestIsNullButMetadataIsProvided() {
        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                null, COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc).isNotNull();
        assertThat(doc.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(doc.getPartnerOrganisation()).isEqualTo(PARTNER_ORG);
        assertThat(doc.getWithdrawalId()).isEqualTo(WITHDRAWAL_ID);
        assertThat(doc.getEtag()).isEqualTo(ETAG);
        assertThat(doc.getSubmissionCompanyName()).isNull();
        assertThat(doc.getPartnerCaseReference()).isNull();
        assertThat(doc.getPartnerObjectionWorkstream()).isNull();
        assertThat(doc.getPartnerContactEmail()).isNull();
    }

    // -----------------------------------------------------------------------
    // toWithdrawAllObjections201Response – document → response
    // -----------------------------------------------------------------------

    @Test
    void toWithdrawAllObjections201Response_mapsAllDocumentFields_whenDocumentIsFullyPopulated() {
        Instant now = Instant.now();
        WithdrawalDocument doc = buildSavedDocument(now);

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getWithdrawalId()).isEqualTo(WITHDRAWAL_ID);
        assertThat(response.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(response.getSubmissionCompanyName()).isEqualTo("ACME LTD");
        assertThat(response.getPartnerCaseReference()).isEqualTo("CASE-001");
        assertThat(response.getPartnerContactEmail()).isEqualTo("owner@example.com");
        assertThat(response.getEtag()).isEqualTo(ETAG);
        assertThat(response.getKind()).isEqualTo("strike-off-partner-objection#withdrawal");
    }

    @Test
    void toWithdrawAllObjections201Response_convertsProcessingStatusToEnum_whenProcessingStatusIsSet() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setProcessingStatus(WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED.getValue());

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getProcessingStatus())
                .isEqualTo(WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED);
    }

    @Test
    void toWithdrawAllObjections201Response_convertsWorkstreamToEnum_whenWorkstreamIsSet() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT.getValue());

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getPartnerObjectionWorkstream())
                .isEqualTo(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
    }

    @Test
    void toWithdrawAllObjections201Response_convertsCreatedAtToOffsetDateTime_whenCreatedAtIsSet() {
        Instant now = Instant.now();
        WithdrawalDocument doc = buildSavedDocument(now);

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getCreatedAt()).isEqualTo(now.atOffset(ZoneOffset.UTC));
    }

    @Test
    void toWithdrawAllObjections201Response_mapsLinks_whenLinksAreSet() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getLinks()).isNotNull();
        assertThat(response.getLinks().getSelf())
                .isEqualTo("/company/12345678/strike-off-partner-objections-withdrawals/wdl-abc-123");
        assertThat(response.getLinks().getCompanyProfile())
                .isEqualTo("/company/12345678");
    }

    @Test
    void toWithdrawAllObjections201Response_mapsProcessingStatusToNull_whenProcessingStatusIsNull() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setProcessingStatus(null);

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getProcessingStatus()).isNull();
    }

    @Test
    void toWithdrawAllObjections201Response_mapsWorkstreamToNull_whenWorkstreamIsNull() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setPartnerObjectionWorkstream(null);

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getPartnerObjectionWorkstream()).isNull();
    }

    @Test
    void toWithdrawAllObjections201Response_mapsCreatedAtToNull_whenCreatedAtIsNull() {
        WithdrawalDocument doc = buildSavedDocument(null);

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getCreatedAt()).isNull();
    }

    @Test
    void toWithdrawAllObjections201Response_mapsLinksToNull_whenLinksAreNull() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setLinks(null);

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getLinks()).isNull();
    }

    @Test
    void toWithdrawAllObjections201Response_leavesFailureReasonAndProcessingStatusChangedAtNull_whenMappingIsPerformed() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());

        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(doc);

        assertThat(response.getFailureReason()).isNull();
        assertThat(response.getProcessingStatusChangedAt()).isNull();
    }

    @Test
    void toWithdrawAllObjections201Response_returnsNull_whenDocumentIsNull() {
        WithdrawAllObjections201Response response =
                mapper.toWithdrawAllObjections201Response(null);

        assertThat(response).isNull();
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private WithdrawAllObjectionsRequest buildRequest() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        request.setSubmissionCompanyName("ACME LTD");
        request.setPartnerCaseReference("CASE-001");
        request.setPartnerContactEmail("owner@example.com");
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
        return request;
    }

    private WithdrawalDocument buildSavedDocument(Instant createdAt) {
        WithdrawalDocument doc = new WithdrawalDocument();
        doc.setCompanyNumber(COMPANY_NUMBER);
        doc.setWithdrawalId(WITHDRAWAL_ID);
        doc.setEtag(ETAG);
        doc.setSubmissionCompanyName("ACME LTD");
        doc.setPartnerCaseReference("CASE-001");
        doc.setPartnerContactEmail("owner@example.com");
        doc.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT.getValue());
        doc.setPartnerOrganisation(PARTNER_ORG);
        doc.setProcessingStatus(WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED.getValue());
        doc.setKind("strike-off-partner-objection#withdrawal");
        doc.setCreatedAt(createdAt);

        WithdrawalLinks links = new WithdrawalLinks();
        links.setSelf("/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/" + WITHDRAWAL_ID);
        links.setCompanyProfile("/company/" + COMPANY_NUMBER);
        doc.setLinks(links);

        return doc;
    }
}
