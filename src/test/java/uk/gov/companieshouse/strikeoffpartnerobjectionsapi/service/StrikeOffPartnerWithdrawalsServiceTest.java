package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawalRequestedStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalLinks;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerWithdrawalsServiceTest {

    private static final String COMPANY_NUMBER = "12345678";

    @Mock
    private WithdrawalRepository withdrawalRepository;

    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @BeforeEach
    void setUp() {
        strikeOffPartnerWithdrawalsService =
                new StrikeOffPartnerWithdrawalsService(withdrawalRepository);
    }

    @Test
    void withdrawAllObjections_persistsDocumentAndReturnsMappedResponse_whenRequestIsValid() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument savedDocument = buildSavedDocument();

        when(withdrawalRepository.insert(any(WithdrawalDocument.class))).thenReturn(savedDocument);

        WithdrawAllObjections201Response response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalRepository).insert(any(WithdrawalDocument.class));

        assertThat(response.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(response.getSubmissionCompanyName()).isEqualTo("ACME LTD");
        assertThat(response.getWithdrawalId()).isEqualTo(savedDocument.getWithdrawalId());
        assertThat(response.getPartnerCaseReference()).isEqualTo("CASE-001");
        assertThat(response.getPartnerContactEmail()).isEqualTo("owner@example.com");
        assertThat(response.getPartnerObjectionWorkstream()).isEqualTo(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
        assertThat(response.getProcessingStatus()).isEqualTo(WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED);
        assertThat(response.getKind()).isEqualTo("strike-off-partner-objection#withdrawal");
        assertThat(response.getEtag()).isNotBlank();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getLinks()).isNotNull();
        assertThat(response.getLinks().getSelf()).contains(
                "/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/");
        assertThat(response.getLinks().getCompanyProfile()).isEqualTo("/company/" + COMPANY_NUMBER);
    }

    @Test
    void withdrawAllObjections_mapsRequestFieldsOntoPersistedDocument_whenRequestIsValid() {
        WithdrawAllObjectionsRequest request = buildRequest();
        ArgumentCaptor<WithdrawalDocument> documentCaptor =
                ArgumentCaptor.forClass(WithdrawalDocument.class);

        when(withdrawalRepository.insert(any(WithdrawalDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalRepository).insert(documentCaptor.capture());
        WithdrawalDocument captured = documentCaptor.getValue();

        assertThat(captured.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(captured.getSubmissionCompanyName()).isEqualTo("ACME LTD");
        assertThat(captured.getPartnerCaseReference()).isEqualTo("CASE-001");
        assertThat(captured.getPartnerContactEmail()).isEqualTo("owner@example.com");
        assertThat(captured.getPartnerObjectionWorkstream())
                .isEqualTo(PartnerObjectionWorkstream.DEBT_MANAGEMENT.getValue());
        assertThat(captured.getPartnerOrganisation()).isEqualTo("hmrc");
        assertThat(captured.getProcessingStatus()).isEqualTo("withdrawal-requested");
        assertThat(captured.getKind()).isEqualTo("strike-off-partner-objection#withdrawal");
        assertThat(captured.getEtag()).isNotBlank();
        assertThat(captured.getWithdrawalId()).isNotBlank();
        assertDoesNotThrowUUID(captured.getWithdrawalId());
    }

    @Test
    void withdrawAllObjections_setsWithdrawalRequestedStatus_whenCreatingDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();
        ArgumentCaptor<WithdrawalDocument> captor = ArgumentCaptor.forClass(WithdrawalDocument.class);

        when(withdrawalRepository.insert(any(WithdrawalDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalRepository).insert(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo("withdrawal-requested");
    }

    @Test
    void withdrawAllObjections_generatesUniqueWithdrawalId_onEachCall() {
        WithdrawAllObjectionsRequest request = buildRequest();
        ArgumentCaptor<WithdrawalDocument> captor = ArgumentCaptor.forClass(WithdrawalDocument.class);

        when(withdrawalRepository.insert(any(WithdrawalDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalRepository, org.mockito.Mockito.times(2)).insert(captor.capture());
        var ids = captor.getAllValues().stream()
                .map(WithdrawalDocument::getWithdrawalId)
                .toList();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
    }

    @Test
    void withdrawAllObjections_generatesEtag_whenCreatingDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();
        ArgumentCaptor<WithdrawalDocument> captor = ArgumentCaptor.forClass(WithdrawalDocument.class);

        when(withdrawalRepository.insert(any(WithdrawalDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalRepository).insert(captor.capture());
        assertThat(captor.getValue().getEtag()).isNotBlank();
        assertDoesNotThrowUUID(captor.getValue().getEtag());
    }

    @Test
    void withdrawAllObjections_buildsCorrectSelfAndCompanyProfileLinks_whenCreatingDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();
        ArgumentCaptor<WithdrawalDocument> captor = ArgumentCaptor.forClass(WithdrawalDocument.class);

        when(withdrawalRepository.insert(any(WithdrawalDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalRepository).insert(captor.capture());
        WithdrawalDocument doc = captor.getValue();
        assertThat(doc.getLinks()).isNotNull();
        assertThat(doc.getLinks().getSelf()).isEqualTo(
                "/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/" + doc.getWithdrawalId());
        assertThat(doc.getLinks().getCompanyProfile()).isEqualTo("/company/" + COMPANY_NUMBER);
    }

    @Test
    void withdrawAllObjections_throwsWithdrawalPersistenceException_whenRepositoryInsertFails() {
        WithdrawAllObjectionsRequest request = buildRequest();
        DataAccessResourceFailureException cause =
                new DataAccessResourceFailureException("mongo insert failed");

        when(withdrawalRepository.insert(any(WithdrawalDocument.class))).thenThrow(cause);

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request))
                .isInstanceOf(WithdrawalPersistenceException.class)
                .hasMessage("Failed to persist withdrawal")
                .hasCause(cause);

        verify(withdrawalRepository).insert(any(WithdrawalDocument.class));
    }

    private WithdrawAllObjectionsRequest buildRequest() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        request.setSubmissionCompanyName("ACME LTD");
        request.setPartnerCaseReference("CASE-001");
        request.setPartnerContactEmail("owner@example.com");
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
        return request;
    }

    private WithdrawalDocument buildSavedDocument() {
        String withdrawalId = UUID.randomUUID().toString();
        WithdrawalDocument doc = new WithdrawalDocument();
        doc.setCompanyNumber(COMPANY_NUMBER);
        doc.setSubmissionCompanyName("ACME LTD");
        doc.setWithdrawalId(withdrawalId);
        doc.setPartnerCaseReference("CASE-001");
        doc.setPartnerContactEmail("owner@example.com");
        doc.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT.getValue());
        doc.setPartnerOrganisation("hmrc");
        doc.setProcessingStatus("withdrawal-requested");
        doc.setEtag(UUID.randomUUID().toString());
        doc.setKind("strike-off-partner-objection#withdrawal");

        WithdrawalLinks links = new WithdrawalLinks();
        links.setSelf("/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/" + withdrawalId);
        links.setCompanyProfile("/company/" + COMPANY_NUMBER);
        doc.setLinks(links);

        doc.setCreatedAt(Instant.now());

        return doc;
    }

    private void assertDoesNotThrowUUID(String value) {
        assertThat(value).isNotBlank();
        UUID parsed = UUID.fromString(value);
        assertThat(parsed).isNotNull();
    }
}