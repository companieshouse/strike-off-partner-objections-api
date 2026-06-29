package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.api.objections.model.WithdrawalProcessingStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerLinks;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

@Tag("unit-test")
class WithdrawalMapperTest {

    private final WithdrawalMapper mapper = new WithdrawalMapperImpl();

    private static final String COMPANY_NUMBER = "12345678";
    private static final String PARTNER_ORG = "hmrc";
    private static final String WITHDRAWAL_ID = "wdl-abc-123";
    private static final String ETAG = "etag-xyz";
    private static final String DEBT_MANAGEMENT_WORKSTREAM = "debt-management";

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
    void toWithdrawalDocument_mapsWorkstreamAsString_whenWorkstreamIsSet() {
        WithdrawAllObjectionsRequest request = buildRequest();
        request.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);

        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                request, COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc.getPartnerObjectionWorkstream())
                .isEqualTo(DEBT_MANAGEMENT_WORKSTREAM);
    }

    @Test
    void toWithdrawalDocument_setsWithdrawalRequestedProcessingStatus_whenDocumentIsCreated() {
        WithdrawalDocument doc = mapper.toWithdrawalDocument(
                new WithdrawAllObjectionsRequest(), COMPANY_NUMBER, PARTNER_ORG, WITHDRAWAL_ID, ETAG);

        assertThat(doc.getProcessingStatus())
                .isEqualTo(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED.getValue());
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
    // toWithdrawAllObjectionsResponse – document → response (used by POST)
    // -----------------------------------------------------------------------

    @Test
    void toWithdrawAllObjectionsResponse_mapsAllDocumentFields_whenDocumentIsFullyPopulated_forCreatePath() {
        Instant now = Instant.now();
        WithdrawalDocument doc = buildSavedDocument(now);

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getWithdrawalId()).isEqualTo(WITHDRAWAL_ID);
        assertThat(response.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(response.getSubmissionCompanyName()).isEqualTo("ACME LTD");
        assertThat(response.getPartnerCaseReference()).isEqualTo("CASE-001");
        assertThat(response.getPartnerContactEmail()).isEqualTo("owner@example.com");
        assertThat(response.getEtag()).isEqualTo(ETAG);
        assertThat(response.getKind()).isEqualTo("strike-off-partner-objection#withdrawal");
    }

    @Test
    void toWithdrawAllObjectionsResponse_convertsProcessingStatusToEnum_whenProcessingStatusIsSet_forCreatePath() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED.getValue());

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getProcessingStatus())
                .isEqualTo(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED);
    }

    @Test
    void toWithdrawAllObjectionsResponse_mapsWorkstreamString_whenWorkstreamIsSet_forCreatePath() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getPartnerObjectionWorkstream())
                .isEqualTo(DEBT_MANAGEMENT_WORKSTREAM);
    }

    @Test
    void toWithdrawAllObjectionsResponse_convertsCreatedAtToOffsetDateTime_whenCreatedAtIsSet_forCreatePath() {
        Instant now = Instant.now();
        WithdrawalDocument doc = buildSavedDocument(now);

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getCreatedAt()).isEqualTo(now.atOffset(ZoneOffset.UTC));
    }

    @Test
    void toWithdrawAllObjectionsResponse_mapsLinks_whenLinksAreSet_forCreatePath() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getLinks()).isNotNull();
        assertThat(response.getLinks().getSelf())
                .isEqualTo("/company/12345678/strike-off-partner-objections-withdrawals/wdl-abc-123");
        assertThat(response.getLinks().getCompanyProfile())
                .isEqualTo("/company/12345678");
    }

    @Test
    void toWithdrawAllObjectionsResponse_mapsProcessingStatusToNull_whenProcessingStatusIsNull_forCreatePath() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setProcessingStatus(null);

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getProcessingStatus()).isNull();
    }

    @Test
    void toWithdrawAllObjectionsResponse_mapsWorkstreamToNull_whenWorkstreamIsNull_forCreatePath() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setPartnerObjectionWorkstream(null);

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getPartnerObjectionWorkstream()).isNull();
    }

    @Test
    void toWithdrawAllObjectionsResponse_mapsCreatedAtToNull_whenCreatedAtIsNull_forCreatePath() {
        WithdrawalDocument doc = buildSavedDocument(null);

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getCreatedAt()).isNull();
    }

    @Test
    void toWithdrawAllObjectionsResponse_mapsLinksToNull_whenLinksAreNull_forCreatePath() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());
        doc.setLinks(null);

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getLinks()).isNull();
    }

    @Test
    void toWithdrawAllObjectionsResponse_leavesFailureReasonAndProcessingStatusChangedAtNull_whenMappingIsPerformed_forCreatePath() {
        WithdrawalDocument doc = buildSavedDocument(Instant.now());

        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(doc);

        assertThat(response.getFailureReason()).isNull();
        assertThat(response.getProcessingStatusChangedAt()).isNull();
    }

    @Test
    void toWithdrawAllObjectionsResponse_returnsNull_whenDocumentIsNull_forCreatePath() {
        WithdrawAllObjectionsResponse response =
                mapper.toWithdrawAllObjectionsResponse(null);

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
        request.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
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
        doc.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
        doc.setPartnerOrganisation(PARTNER_ORG);
        doc.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED.getValue());
        doc.setKind("strike-off-partner-objection#withdrawal");
        doc.setCreatedAt(createdAt);

        PartnerLinks links = new PartnerLinks();
        links.setSelf("/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/" + WITHDRAWAL_ID);
        links.setCompanyProfile("/company/" + COMPANY_NUMBER);
        doc.setLinks(links);

        return doc;
    }
}