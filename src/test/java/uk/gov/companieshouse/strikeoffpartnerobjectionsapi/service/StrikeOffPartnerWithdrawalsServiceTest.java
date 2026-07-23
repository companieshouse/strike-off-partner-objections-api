package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import jakarta.servlet.http.HttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.api.objections.model.UpdateWithdrawalStatusRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawalProcessingStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalNotFoundException;
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

    @Mock
    private CompanyValidator companyValidator;

    @Mock
    private HttpServletRequest defaultRequest;

    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        strikeOffPartnerWithdrawalsService =
                new StrikeOffPartnerWithdrawalsService(withdrawalRepository, withdrawalMapper,
                        withdrawalKafkaProducer, companyValidator, validator, defaultRequest);
        lenient().when(defaultRequest.getHeader("ERIC-Authorised-Application-Partner-Organisation")).thenReturn(PARTNER_ORGANISATION);
    }

    // ===== GET Withdrawal Tests =====

    @Test
    void getWithdrawal_whenRetrieving_delegatesToRepositoryToFindByCompanyNumberAndWithdrawalId() {
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
    void getWithdrawal_whenWithdrawalIsFound_mapsDocumentToResponse() {
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
    void getWithdrawal_whenWithdrawalIsFound_returnsResponseFromMapper() {
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
    void getWithdrawal_whenWithdrawalIsNotFound_throwsNotFoundException() {
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
    void getWithdrawal_whenCompanyNumberDoesNotMatch_throwsNotFoundException() {
        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getWithdrawal_whenRepositoryThrowsDataAccessException_throwsWithdrawalPersistenceException() {
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

    @Test
    void getWithdrawal_whenPartnerOrganisationMissingInRequestContext_throwsIllegalStateException() {
        when(defaultRequest.getHeader("ERIC-Authorised-Application-Partner-Organisation")).thenReturn(null);

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Missing partner organisation in request context");

        verifyNoInteractions(withdrawalRepository, withdrawalMapper);
    }

    @Test
    void getWithdrawal_whenHttpServletRequestIsNotConfigured_throwsIllegalStateException() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        StrikeOffPartnerWithdrawalsService serviceWithoutExtractor =
                new StrikeOffPartnerWithdrawalsService(withdrawalRepository, withdrawalMapper,
                        withdrawalKafkaProducer, companyValidator, validator);

        assertThatThrownBy(() -> serviceWithoutExtractor.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("HttpServletRequest is not configured");

        verifyNoInteractions(withdrawalRepository, withdrawalMapper);
    }

    // ===== Company Validation Tests =====

    @Test
    void withdrawAllObjections_whenCompanyValidationFails_throwsCompanyValidationException() {
        WithdrawAllObjectionsRequest request = buildRequest();
        CompanyValidationException validationException =
            new CompanyValidationException("Company not found", "COMPANY_NUMBER_NOT_EXIST");

        doThrow(validationException).when(companyValidator).validateCompany(COMPANY_NUMBER, request.getSubmissionCompanyName());

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request))
                .isSameAs(validationException);

        verify(companyValidator).validateCompany(COMPANY_NUMBER, request.getSubmissionCompanyName());
        verifyNoInteractions(withdrawalMapper, withdrawalRepository, withdrawalKafkaProducer);
    }


    @Test
    void withdrawAllObjections_whenCompanyValidationPasses_continuesWithPersistence() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();
        WithdrawalDocument savedDocument = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(
                eq(request), eq(COMPANY_NUMBER), eq("hmrc"), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(savedDocument))
                .thenReturn(getPublishedEvent("event-id-validation-1"));
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(savedDocument))
                .thenReturn(new WithdrawAllObjectionsResponse());

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(companyValidator).validateCompany(COMPANY_NUMBER, request.getSubmissionCompanyName());
        verify(withdrawalRepository).insert(mappedDocument);
        verify(withdrawalKafkaProducer).publishWithdrawalEvent(savedDocument);
    }

    // ===== POST Withdrawal Tests (Existing Tests) =====

    @Test
    void withdrawAllObjections_whenRequestIsValid_delegatesToMapperToCreateDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();
        WithdrawalDocument savedDocument = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(
                eq(request), eq(COMPANY_NUMBER), eq("hmrc"), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(savedDocument))
                .thenReturn(getPublishedEvent("event-id-0"));
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(savedDocument))
                .thenReturn(new WithdrawAllObjectionsResponse());

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalMapper).toWithdrawalDocument(
                eq(request), eq(COMPANY_NUMBER), eq("hmrc"), any(), any());
    }

    @Test
    void withdrawAllObjections_whenRequestIsValid_persistsDocumentReturnedByMapper() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(mappedDocument);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(mappedDocument))
                .thenReturn(getPublishedEvent("event-id-1"));

        when(withdrawalMapper.toWithdrawAllObjectionsResponse(mappedDocument))
                .thenReturn(new WithdrawAllObjectionsResponse());

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalRepository).insert(mappedDocument);
        verify(withdrawalKafkaProducer).publishWithdrawalEvent(mappedDocument);
         ArgumentCaptor<WithdrawalDocument> saveCaptor = ArgumentCaptor.forClass(WithdrawalDocument.class);
         verify(withdrawalRepository).save(saveCaptor.capture());
         assertThat(saveCaptor.getValue().getEventStatus()).isEqualTo(EventStatus.PUBLISHED);
         assertThat(saveCaptor.getValue().getEventCorrelationId()).isNotBlank();
    }

    @Test
    void withdrawAllObjections_whenKafkaPublishFails_marksEventAsFailedAndRethrows() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();
        WithdrawalDocument savedDocument = buildSavedDocument();
        KafkaPublishException kafkaException =
                new KafkaPublishException("publish failed", "event-id-2", new RuntimeException("boom"));

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(savedDocument)).thenThrow(kafkaException);

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request))
                .isSameAs(kafkaException);

         ArgumentCaptor<WithdrawalDocument> saveCaptor = ArgumentCaptor.forClass(WithdrawalDocument.class);
         verify(withdrawalRepository).save(saveCaptor.capture());
         assertThat(saveCaptor.getValue().getEventStatus()).isEqualTo(EventStatus.FAILED);
         assertThat(saveCaptor.getValue().getEventFailureReason()).contains("publish failed");
        assertThat(saveCaptor.getValue().getEventCorrelationId()).isNotBlank();
    }

    @Test
    void withdrawAllObjections_whenSaveAfterPublishFails_stillReturnsSuccessResponse() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();
        WithdrawalDocument savedDocument = buildSavedDocument();
        WithdrawAllObjectionsResponse expectedResponse = buildResponse(savedDocument);
        DataAccessResourceFailureException saveException =
                new DataAccessResourceFailureException("mongo update failed");

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(savedDocument))
                .thenReturn(getPublishedEvent("event-id-3"));
        when(withdrawalRepository.save(any(WithdrawalDocument.class))).thenThrow(saveException);
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(savedDocument)).thenReturn(expectedResponse);

        WithdrawAllObjectionsResponse result =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        assertThat(result).isSameAs(expectedResponse);
        verify(withdrawalRepository).save(any(WithdrawalDocument.class));
    }

    @Test
    void withdrawAllObjections_whenKafkaFailsAndSaveAfterFailureThrows_stillRethrowsOriginalKafkaException() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument mappedDocument = buildSavedDocument();
        WithdrawalDocument savedDocument = buildSavedDocument();
        KafkaPublishException kafkaException =
                new KafkaPublishException("publish failed", "event-id-4", new RuntimeException("boom"));
        DataAccessResourceFailureException saveException =
                new DataAccessResourceFailureException("mongo update failed");

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(mappedDocument);
        when(withdrawalRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(savedDocument)).thenThrow(kafkaException);
        when(withdrawalRepository.save(any(WithdrawalDocument.class))).thenThrow(saveException);

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request))
                .isSameAs(kafkaException);

        verify(withdrawalRepository).save(any(WithdrawalDocument.class));
    }

    @Test
    void withdrawAllObjections_whenDocumentIsPersisted_returnsResponseFromMapper() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument savedDocument = buildSavedDocument();
        WithdrawAllObjectionsResponse expectedResponse = buildResponse(savedDocument);

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(savedDocument);
        when(withdrawalRepository.insert(savedDocument)).thenReturn(savedDocument);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(savedDocument))
                .thenReturn(getPublishedEvent("event-id-5"));
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(savedDocument))
                .thenReturn(expectedResponse);

        WithdrawAllObjectionsResponse result =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        verify(withdrawalMapper).toWithdrawAllObjectionsResponse(savedDocument);
        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    void withdrawAllObjections_whenCalledMultipleTimes_generatesUniqueWithdrawalId() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument doc = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(doc);
        when(withdrawalRepository.insert(any(WithdrawalDocument.class))).thenReturn(doc);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(any(WithdrawalDocument.class)))
                .thenReturn(getPublishedEvent("event-id-6"));
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
    void withdrawAllObjections_whenCalledMultipleTimes_generatesUniqueEtag() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawalDocument doc = buildSavedDocument();

        when(withdrawalMapper.toWithdrawalDocument(any(), any(), any(), any(), any()))
                .thenReturn(doc);
        when(withdrawalRepository.insert(any(WithdrawalDocument.class))).thenReturn(doc);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(any(WithdrawalDocument.class)))
                .thenReturn(getPublishedEvent("event-id-7"));
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
    void withdrawAllObjections_whenRepositoryInsertFails_throwsWithdrawalPersistenceException() {
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

    @Test
    void withdrawAllObjections_whenPartnerOrganisationMissingInRequestContext_throwsIllegalStateException() {
        WithdrawAllObjectionsRequest request = buildRequest();
        when(this.defaultRequest.getHeader("ERIC-Authorised-Application-Partner-Organisation")).thenReturn(null);

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Missing partner organisation in request context");

        verifyNoInteractions(withdrawalMapper, withdrawalRepository, withdrawalKafkaProducer, companyValidator);
    }

    @Test
    void withdrawAllObjections_whenHttpServletRequestIsNotConfigured_throwsIllegalStateException() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        StrikeOffPartnerWithdrawalsService serviceWithoutExtractor =
                new StrikeOffPartnerWithdrawalsService(withdrawalRepository, withdrawalMapper,
                        withdrawalKafkaProducer, companyValidator, validator);
        WithdrawAllObjectionsRequest request = buildRequest();

        assertThatThrownBy(() -> serviceWithoutExtractor.withdrawAllObjections(COMPANY_NUMBER, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("HttpServletRequest is not configured");

        verifyNoInteractions(withdrawalMapper, withdrawalRepository, withdrawalKafkaProducer, companyValidator);
    }

    @Test
    void updateWithdrawalProcessingStatus_whenValidStatus_updatesAndPersists() {
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();
        request.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);
        WithdrawalDocument existing = buildSavedDocument();
        existing.setProcessingStatus("withdrawal-requested");

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(existing));
        when(withdrawalRepository.save(any(WithdrawalDocument.class))).thenReturn(existing);

        strikeOffPartnerWithdrawalsService
                .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request);

        ArgumentCaptor<WithdrawalDocument> captor = ArgumentCaptor.forClass(WithdrawalDocument.class);
        verify(withdrawalRepository).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo("withdrawal-processing");
        assertThat(captor.getValue().getEtag()).isNotBlank();
    }

    @Test
    void updateWithdrawalProcessingStatus_whenSameStatus_returnsWithoutSaving() {
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();
        request.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED);
        WithdrawalDocument existing = buildSavedDocument();
        existing.setProcessingStatus("withdrawal-requested");

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(existing));

        strikeOffPartnerWithdrawalsService
                .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request);

        verify(withdrawalRepository).findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID);
    }

    @Test
    void updateWithdrawalProcessingStatus_whenWithdrawalMissing_throwsNotFound() {
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();
        request.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService
                .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(WithdrawalNotFoundException.class)
                .hasMessageContaining(COMPANY_NUMBER)
                .hasMessageContaining(WITHDRAWAL_ID);
    }

    @Test
    void updateWithdrawalProcessingStatus_whenStatusMissing_throwsBadRequest() {
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();
        WithdrawalDocument existing = buildSavedDocument();
        existing.setProcessingStatus("withdrawal-requested");

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService
                .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(400);
    }

    @Test
    void updateWithdrawalProcessingStatus_whenRepositorySaveFails_throwsPersistenceException() {
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();
        request.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);
        WithdrawalDocument existing = buildSavedDocument();
        existing.setProcessingStatus("withdrawal-requested");
        DataAccessResourceFailureException cause =
                new DataAccessResourceFailureException("mongo update failed");

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(existing));
        when(withdrawalRepository.save(any(WithdrawalDocument.class))).thenThrow(cause);

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService
                .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(WithdrawalPersistenceException.class)
                .hasMessage("Failed to persist updated withdrawal processing status")
                .hasCause(cause);
    }

    private StrikeOffPartnerObjections getPublishedEvent(String eventId) {
        return StrikeOffPartnerObjections.newBuilder()
                .setEventId(eventId)
                .setEventType(EventType.WITHDRAWAL)
                .setEventTime(Instant.now().toString())
                .setSource("strike-off-partner-objections-api")
                .setCompanyNumber(COMPANY_NUMBER)
                .setPartnerOrganisation("hmrc")
                .setStrikeOffEventId(UUID.randomUUID().toString())
                .build();
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