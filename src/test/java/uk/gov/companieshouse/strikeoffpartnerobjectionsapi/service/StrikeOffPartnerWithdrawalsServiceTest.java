package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalLinks;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerWithdrawalsServiceTest {

    private static final String COMPANY_NUMBER = "12345678";

    @Mock
    private WithdrawalRepository withdrawalRepository;

    @Mock
    private WithdrawalMapper withdrawalMapper;

    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @BeforeEach
    void setUp() {
        strikeOffPartnerWithdrawalsService =
                new StrikeOffPartnerWithdrawalsService(withdrawalRepository, withdrawalMapper);
    }

    @Test
    void withdrawAllObjections_delegatesToMapperToCreateDocument_whenRequestIsValid() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();
        WithdrawalDocument savedDocument = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(
                eq(request), eq(COMPANY_NUMBER), eq("hmrc"), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(withdrawalMapper.toWithdrawAllObjections201Response(savedDocument))
                .thenReturn(new WithdrawAllObjections201Response());

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalMapper).toWithdrawalDocument(
                eq(request), eq(COMPANY_NUMBER), eq("hmrc"), any(), any());
    }

    @Test
    void withdrawAllObjections_persistsDocumentReturnedByMapper_whenRequestIsValid() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert((WithdrawalDocument) mappedDocument)).thenReturn(mappedDocument);
        when(withdrawalMapper.toWithdrawAllObjections201Response(mappedDocument))
                .thenReturn(new WithdrawAllObjections201Response());

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalRepository).insert(mappedDocument);
    }

    @Test
    void withdrawAllObjections_returnsResponseFromMapper_whenDocumentIsPersisted() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument savedDocument = buildSavedDocument();
        WithdrawAllObjections201Response expectedResponse = buildResponse(savedDocument);

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(savedDocument);
        when(withdrawalRepository.insert((WithdrawalDocument) savedDocument)).thenReturn(savedDocument);
        when(withdrawalMapper.toWithdrawAllObjections201Response(savedDocument))
                .thenReturn(expectedResponse);

        WithdrawAllObjections201Response result =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalMapper).toWithdrawAllObjections201Response(savedDocument);
        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    void withdrawAllObjections_generatesUniqueWithdrawalId_onEachCall() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument doc = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(doc);
        when(withdrawalRepository.insert(any(WithdrawalDocument.class))).thenReturn(doc);
        when(withdrawalMapper.toWithdrawAllObjections201Response(any()))
                .thenReturn(new WithdrawAllObjections201Response());

        ArgumentCaptor<String> withdrawalIdCaptor = ArgumentCaptor.forClass(String.class);

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalMapper, org.mockito.Mockito.times(2))
                .toWithdrawalDocument(any(), any(), any(), withdrawalIdCaptor.capture(), any());

        assertThat(withdrawalIdCaptor.getAllValues().get(0))
                .isNotEqualTo(withdrawalIdCaptor.getAllValues().get(1));
    }

    @Test
    void withdrawAllObjections_generatesUniqueEtag_onEachCall() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument doc = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(doc);
        when(withdrawalRepository.insert(any(WithdrawalDocument.class))).thenReturn(doc);
        when(withdrawalMapper.toWithdrawAllObjections201Response(any()))
                .thenReturn(new WithdrawAllObjections201Response());

        ArgumentCaptor<String> etagCaptor = ArgumentCaptor.forClass(String.class);

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalMapper, org.mockito.Mockito.times(2))
                .toWithdrawalDocument(any(), any(), any(), any(), etagCaptor.capture());

        assertThat(etagCaptor.getAllValues().get(0))
                .isNotEqualTo(etagCaptor.getAllValues().get(1));
    }

    @Test
    void withdrawAllObjections_throwsWithdrawalPersistenceException_whenRepositoryInsertFails() {
        WithdrawAllObjectionsRequest request = buildRequest();
        DataAccessResourceFailureException cause =
                new DataAccessResourceFailureException("mongo insert failed");

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(buildSavedDocument());
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

    private WithdrawAllObjections201Response buildResponse(WithdrawalDocument doc) {
        WithdrawAllObjections201Response response = new WithdrawAllObjections201Response();
        response.setCompanyNumber(doc.getCompanyNumber());
        response.setWithdrawalId(doc.getWithdrawalId());
        response.setProcessingStatus(WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED);
        return response;
    }
}