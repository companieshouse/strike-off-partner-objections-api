package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.api.objections.model.WithdrawalProcessingStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.WithdrawalKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerLinks;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerWithdrawalsServiceTest {

    private static final String COMPANY_NUMBER = "12345678";
    private static final String WITHDRAWAL_ID = "withdrawal-123";
    private static final String DEBT_MANAGEMENT_WORKSTREAM = "debt-management";

    @Mock
    private WithdrawalRepository withdrawalRepository;

    @Mock
    private WithdrawalMapper withdrawalMapper;

    @Mock
    private WithdrawalKafkaProducer withdrawalKafkaProducer;

    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @BeforeEach
    void setUp() {
        strikeOffPartnerWithdrawalsService =
                new StrikeOffPartnerWithdrawalsService(withdrawalRepository, withdrawalMapper, withdrawalKafkaProducer);
    }

    // ===== GET Withdrawal Tests =====

    @Test
    void getWithdrawal_delegatesToRepositoryToFindByCompanyNumberAndWithdrawalId_whenRetrieving() {
        WithdrawalDocument document = buildSavedDocument();
        WithdrawAllObjectionsResponse response = new WithdrawAllObjectionsResponse();

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(document));
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(document))
                .thenReturn(response);

        strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID);

        verify(withdrawalRepository).findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID);
    }

    @Test
    void getWithdrawal_mapsDocumentToResponse_whenWithdrawalFound() {
        WithdrawalDocument document = buildSavedDocument();
        WithdrawAllObjectionsResponse response = new WithdrawAllObjectionsResponse();

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(document));
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(document))
                .thenReturn(response);

        strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID);

        verify(withdrawalMapper).toWithdrawAllObjectionsResponse(document);
    }

    @Test
    void getWithdrawal_returnsResponseFromMapper_whenWithdrawalFound() {
        WithdrawalDocument document = buildSavedDocument();
        WithdrawAllObjectionsResponse expectedResponse = new WithdrawAllObjectionsResponse();
        expectedResponse.setWithdrawalId(WITHDRAWAL_ID);
        expectedResponse.setCompanyNumber(COMPANY_NUMBER);

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(document));
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(document))
                .thenReturn(expectedResponse);

        WithdrawAllObjectionsResponse result =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID);

        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    void getWithdrawal_throwsNotFoundException_whenWithdrawalNotFound() {
        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND)
                .hasMessageContaining(WITHDRAWAL_ID)
                .hasMessageContaining(COMPANY_NUMBER);
    }

    @Test
    void getWithdrawal_throwsNotFoundException_whenCompanyNumberDoesNotMatch() {
        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getWithdrawal_throwsWithdrawalPersistenceException_whenRepositoryThrowsDataAccessException() {
        String companyNumber = "12345678";
        String withdrawalId = "withdrawal-123";
        DataAccessException dataAccessException = new DataAccessResourceFailureException("DB down");

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(companyNumber, withdrawalId))
                .thenThrow(dataAccessException);

        WithdrawalPersistenceException ex = assertThrows(
                WithdrawalPersistenceException.class,
                () -> strikeOffPartnerWithdrawalsService.getWithdrawal(companyNumber, withdrawalId));

        assertEquals("Failed to retrieve withdrawal", ex.getMessage());
        assertSame(dataAccessException, ex.getCause());

        verify(withdrawalRepository).findByCompanyNumberAndWithdrawalId(companyNumber, withdrawalId);
        verifyNoInteractions(withdrawalMapper);
    }

    // ===== POST Withdrawal Tests (Existing Tests) =====

    @Test
    void withdrawAllObjections_delegatesToMapperToCreateDocument_whenRequestIsValid() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();
        WithdrawalDocument savedDocument = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(
                eq(request), eq(COMPANY_NUMBER), eq("hmrc"), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(savedDocument))
                .thenReturn(new WithdrawAllObjectionsResponse());

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
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(mappedDocument);
        doNothing().when(withdrawalKafkaProducer)
                .publishWithdrawalEvent(mappedDocument);

        when(withdrawalMapper.toWithdrawAllObjectionsResponse(mappedDocument))
                .thenReturn(new WithdrawAllObjectionsResponse());

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalRepository).insert(mappedDocument);
        verify(withdrawalKafkaProducer).publishWithdrawalEvent(mappedDocument);
        ArgumentCaptor<WithdrawalDocument> saveCaptor = ArgumentCaptor.forClass(WithdrawalDocument.class);
        verify(withdrawalRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getEventStatus()).isEqualTo(EventStatus.PUBLISHED.name());
        assertThat(saveCaptor.getValue().getEventCorrelationId()).isNotBlank();
    }

    @Test
    void withdrawAllObjections_marksEventAsFailedAndRethrows_whenKafkaPublishFails() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();
        WithdrawalDocument savedDocument = buildSavedDocument();
        KafkaPublishException kafkaException = new KafkaPublishException("publish failed", new RuntimeException("boom"));

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(savedDocument);
        doThrow(kafkaException).when(withdrawalKafkaProducer).publishWithdrawalEvent(savedDocument);

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request))
                .isSameAs(kafkaException);

        ArgumentCaptor<WithdrawalDocument> saveCaptor = ArgumentCaptor.forClass(WithdrawalDocument.class);
        verify(withdrawalRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getEventStatus()).isEqualTo(EventStatus.FAILED.name());
        assertThat(saveCaptor.getValue().getEventFailureReason()).contains("publish failed");
        assertThat(saveCaptor.getValue().getEventCorrelationId()).isNotBlank();
    }

    @Test
    void withdrawAllObjections_returnsResponseFromMapper_whenDocumentIsPersisted() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument savedDocument = buildSavedDocument();
        WithdrawAllObjectionsResponse expectedResponse = buildResponse(savedDocument);

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(savedDocument);
        when(withdrawalRepository.insert(savedDocument)).thenReturn(savedDocument);
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(savedDocument))
                .thenReturn(expectedResponse);

        WithdrawAllObjectionsResponse result =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalMapper).toWithdrawAllObjectionsResponse(savedDocument);
        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    void withdrawAllObjections_generatesUniqueWithdrawalId_onEachCall() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument doc = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(doc);
        when(withdrawalRepository.insert(any(WithdrawalDocument.class))).thenReturn(doc);
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(any()))
                .thenReturn(new WithdrawAllObjectionsResponse());

        ArgumentCaptor<String> withdrawalIdCaptor = ArgumentCaptor.forClass(String.class);

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalMapper, org.mockito.Mockito.times(2))
                .toWithdrawalDocument(any(), any(), any(), withdrawalIdCaptor.capture(), any());

        String id1 = withdrawalIdCaptor.getAllValues().get(0);
        String id2 = withdrawalIdCaptor.getAllValues().get(1);
        assertThat(id1).isNotEqualTo(id2);
        assertThat(UUID.fromString(id1)).isNotNull();
        assertThat(UUID.fromString(id2)).isNotNull();
    }

    @Test
    void withdrawAllObjections_generatesUniqueEtag_onEachCall() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument doc = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(doc);
        when(withdrawalRepository.insert(any(WithdrawalDocument.class))).thenReturn(doc);
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(any()))
                .thenReturn(new WithdrawAllObjectionsResponse());

        ArgumentCaptor<String> etagCaptor = ArgumentCaptor.forClass(String.class);

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalMapper, org.mockito.Mockito.times(2))
                .toWithdrawalDocument(any(), any(), any(), any(), etagCaptor.capture());

        String etag1 = etagCaptor.getAllValues().get(0);
        String etag2 = etagCaptor.getAllValues().get(1);
        assertThat(etag1).isNotEqualTo(etag2);
        assertThat(UUID.fromString(etag1)).isNotNull();
        assertThat(UUID.fromString(etag2)).isNotNull();
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
        request.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
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
        doc.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
        doc.setPartnerOrganisation("hmrc");
        doc.setProcessingStatus("withdrawal-requested");
        doc.setEtag(UUID.randomUUID().toString());
        doc.setKind("strike-off-partner-objection#withdrawal");

        PartnerLinks links = new PartnerLinks();
        links.setSelf("/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/" + withdrawalId);
        links.setCompanyProfile("/company/" + COMPANY_NUMBER);
        doc.setLinks(links);

        doc.setCreatedAt(Instant.now());

        return doc;
    }

    private WithdrawAllObjectionsResponse buildResponse(WithdrawalDocument doc) {
        WithdrawAllObjectionsResponse response = new WithdrawAllObjectionsResponse();
        response.setCompanyNumber(doc.getCompanyNumber());
        response.setWithdrawalId(doc.getWithdrawalId());
        response.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED);
        return response;
    }
}