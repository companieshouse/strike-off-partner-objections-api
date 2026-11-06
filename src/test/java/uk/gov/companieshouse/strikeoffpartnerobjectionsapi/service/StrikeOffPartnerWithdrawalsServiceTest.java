package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import com.mongodb.client.result.UpdateResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.HmrcCallbackFailedException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.WithdrawalKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerLinks;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;
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
    private ObjectionRepository objectionRepository;

    @Mock
    private WithdrawalKafkaProducer withdrawalKafkaProducer;

    @Mock
    private CompanyValidator companyValidator;

    @Mock
    private HmrcCallbackService hmrcCallbackService;

    @Mock
    private EmailValidator emailValidator;

    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            strikeOffPartnerWithdrawalsService =
                    new StrikeOffPartnerWithdrawalsService(withdrawalRepository, objectionRepository, withdrawalMapper,
                            withdrawalKafkaProducer, companyValidator, validator, emailValidator, hmrcCallbackService);

            // Default happy-path stub; overridden in specific tests for false/exception scenarios.
            lenient().when(objectionRepository.existsByCompanyNumberAndPartnerOrganisation(
                    COMPANY_NUMBER, PARTNER_ORGANISATION)).thenReturn(true);
        }
    }

    // ===== GET Withdrawal Tests =====

    @Test
    void getWithdrawal_whenWithdrawalIsFound_delegatesAndReturnsResponse() {
        WithdrawalDocument document = buildSavedDocument();
        WithdrawAllObjectionsResponse expectedResponse = new WithdrawAllObjectionsResponse();
        expectedResponse.setWithdrawalId(WITHDRAWAL_ID);
        expectedResponse.setCompanyNumber(COMPANY_NUMBER);

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(document));
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(document))
                .thenReturn(expectedResponse);

        WithdrawAllObjectionsResponse result =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID, PARTNER_ORGANISATION);

        verify(withdrawalRepository).findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID);
        verify(withdrawalMapper).toWithdrawAllObjectionsResponse(document);
        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    void getWithdrawal_whenWithdrawalIsNotFound_throwsNotFoundException() {
        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID, PARTNER_ORGANISATION))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND)
                .hasMessageContaining(WITHDRAWAL_ID)
                .hasMessageContaining(COMPANY_NUMBER);
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
                () -> strikeOffPartnerWithdrawalsService.getWithdrawal(companyNumber, withdrawalId, PARTNER_ORGANISATION));

        assertEquals("Failed to retrieve withdrawal", ex.getMessage());
        assertSame(dataAccessException, ex.getCause());

        verify(withdrawalRepository).findByCompanyNumberAndWithdrawalId(companyNumber, withdrawalId);
        verifyNoInteractions(withdrawalMapper);
    }

    // ===== Company Validation Tests =====

    @Test
    void withdrawAllObjections_whenCompanyValidationFails_throwsCompanyValidationException() {
        WithdrawAllObjectionsRequest request = buildRequest();
        CompanyValidationException validationException =
            new CompanyValidationException("Company not found", "COMPANY_NUMBER_NOT_EXIST");

        doThrow(validationException).when(companyValidator).validateCompanyForWithdrawal(COMPANY_NUMBER, request.getSubmissionCompanyName());

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                .isSameAs(validationException);

        verify(companyValidator).validateCompanyForWithdrawal(COMPANY_NUMBER, request.getSubmissionCompanyName());
        verifyNoInteractions(emailValidator);
        verifyNoInteractions(objectionRepository, withdrawalMapper, withdrawalRepository, withdrawalKafkaProducer);
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

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        verify(companyValidator).validateCompanyForWithdrawal(COMPANY_NUMBER, request.getSubmissionCompanyName());
        verify(objectionRepository).existsByCompanyNumberAndPartnerOrganisation(COMPANY_NUMBER, PARTNER_ORGANISATION);
        verify(emailValidator).validateEmail(request.getPartnerContactEmail());
        verify(withdrawalRepository).insert(mappedDocument);
        verify(withdrawalKafkaProducer).publishWithdrawalEvent(savedDocument);
    }

    @Test
    void withdrawAllObjections_whenObjectionRepositoryThrowsDataAccessException_throwsWithdrawalPersistenceException() {
        WithdrawAllObjectionsRequest request = buildRequest();
        DataAccessResourceFailureException cause =
                new DataAccessResourceFailureException("mongo query failed");

        when(objectionRepository.existsByCompanyNumberAndPartnerOrganisation(COMPANY_NUMBER, PARTNER_ORGANISATION))
                .thenThrow(cause);

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                .isInstanceOf(WithdrawalPersistenceException.class)
                .hasMessage("Failed to validate objections for withdrawal")
                .hasCause(cause);

        verify(companyValidator).validateCompanyForWithdrawal(COMPANY_NUMBER, request.getSubmissionCompanyName());
        verify(objectionRepository).existsByCompanyNumberAndPartnerOrganisation(COMPANY_NUMBER, PARTNER_ORGANISATION);
        verifyNoInteractions(emailValidator);
        verifyNoInteractions(withdrawalMapper, withdrawalRepository, withdrawalKafkaProducer);
    }

    @Test
    void withdrawAllObjections_whenNoObjectionsForPartner_throwsCompanyValidationException() {
        WithdrawAllObjectionsRequest request = buildRequest();

        when(objectionRepository.existsByCompanyNumberAndPartnerOrganisation(COMPANY_NUMBER, PARTNER_ORGANISATION))
                .thenReturn(false);

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                .isInstanceOf(CompanyValidationException.class)
                .hasMessageContaining(COMPANY_NUMBER)
                .hasMessageContaining(PARTNER_ORGANISATION);

        verify(companyValidator).validateCompanyForWithdrawal(COMPANY_NUMBER, request.getSubmissionCompanyName());
        verify(objectionRepository).existsByCompanyNumberAndPartnerOrganisation(COMPANY_NUMBER, PARTNER_ORGANISATION);
        verifyNoInteractions(emailValidator);
        verifyNoInteractions(withdrawalMapper, withdrawalRepository, withdrawalKafkaProducer);
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

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

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
        when(withdrawalRepository.updateEventStatus(anyString(), anyString(), any(), any(), anyString(), any()))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        when(withdrawalMapper.toWithdrawAllObjectionsResponse(mappedDocument))
                .thenReturn(new WithdrawAllObjectionsResponse());

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        verify(withdrawalRepository).insert(mappedDocument);
        verify(withdrawalKafkaProducer).publishWithdrawalEvent(mappedDocument);
        verify(withdrawalRepository).updateEventStatus(
                eq(mappedDocument.getCompanyNumber()), eq(mappedDocument.getWithdrawalId()),
                eq(EventStatus.PUBLISHED), any(), anyString(), eq(null));
        verify(withdrawalRepository, times(0)).save(any(WithdrawalDocument.class));
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
        when(withdrawalRepository.updateEventStatus(anyString(), anyString(), any(), any(), anyString(), any()))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                .isSameAs(kafkaException);

        verify(withdrawalRepository).updateEventStatus(
                eq(savedDocument.getCompanyNumber()), eq(savedDocument.getWithdrawalId()),
                eq(EventStatus.FAILED), any(), anyString(), eq("publish failed"));
        verify(withdrawalRepository, times(0)).save(any(WithdrawalDocument.class));
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
        when(withdrawalRepository.updateEventStatus(anyString(), anyString(), any(), any(), anyString(), any()))
                .thenThrow(saveException);
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(savedDocument)).thenReturn(expectedResponse);

        WithdrawAllObjectionsResponse result =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        assertThat(result).isSameAs(expectedResponse);
        verify(withdrawalRepository).updateEventStatus(
                eq(savedDocument.getCompanyNumber()), eq(savedDocument.getWithdrawalId()),
                eq(EventStatus.PUBLISHED), any(), anyString(), eq(null));
        verify(withdrawalRepository, times(0)).save(any(WithdrawalDocument.class));
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
        when(withdrawalRepository.updateEventStatus(anyString(), anyString(), any(), any(), anyString(), any()))
                .thenThrow(saveException);

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                .isSameAs(kafkaException);

        verify(withdrawalRepository).updateEventStatus(
                eq(savedDocument.getCompanyNumber()), eq(savedDocument.getWithdrawalId()),
                eq(EventStatus.FAILED), any(), anyString(), eq("publish failed"));
    }

    @Test
    void withdrawAllObjections_whenEventStatusUpdateIsUnacknowledged_doesNotFailCreation() {
        stubWithdrawalCreation();
        when(withdrawalRepository.updateEventStatus(
                anyString(), anyString(), any(), any(), anyString(), eq(null)))
                .thenReturn(UpdateResult.unacknowledged());

        WithdrawAllObjectionsResponse response = strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                COMPANY_NUMBER, buildRequest(), PARTNER_ORGANISATION);

        assertThat(response).isNotNull();
        verify(withdrawalRepository).updateEventStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), eq(EventStatus.PUBLISHED), any(), anyString(), eq(null));
    }

    @Test
    void withdrawAllObjections_whenEventStatusDocumentIsUnmatched_doesNotFailCreation() {
        stubWithdrawalCreation();
        when(withdrawalRepository.updateEventStatus(
                anyString(), anyString(), any(), any(), anyString(), eq(null)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThat(strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                COMPANY_NUMBER, buildRequest(), PARTNER_ORGANISATION)).isNotNull();

        verify(withdrawalRepository).updateEventStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), eq(EventStatus.PUBLISHED), any(), anyString(), eq(null));
    }

    @Test
    void withdrawAllObjections_whenEventStatusIsUnchanged_doesNotFailCreation() {
        stubWithdrawalCreation();
        when(withdrawalRepository.updateEventStatus(
                anyString(), anyString(), any(), any(), anyString(), eq(null)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        assertThat(strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                COMPANY_NUMBER, buildRequest(), PARTNER_ORGANISATION)).isNotNull();

        verify(withdrawalRepository).updateEventStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), eq(EventStatus.PUBLISHED), any(), anyString(), eq(null));
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
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

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

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        verify(withdrawalMapper, times(2))
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

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        verify(withdrawalMapper, times(2))
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
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                .isInstanceOf(WithdrawalPersistenceException.class)
                .hasMessage("Failed to persist withdrawal")
                .hasCause(cause);

        verify(withdrawalRepository).insert(any(WithdrawalDocument.class));
    }

    @Test
    void updateWithdrawalProcessingStatus_whenValidStatus_updatesAndPersists() {
        WithdrawalDocument existing = buildSavedDocument();
        existing.setProcessingStatus("withdrawal-requested");

        setupUpdateStatusMocks(existing);

        Instant before = Instant.now();
        strikeOffPartnerWithdrawalsService
                .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID,
                        createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING));

        verify(withdrawalRepository).updateProcessingStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), eq("withdrawal-requested"),
                eq("withdrawal-processing"), any(), anyString());
        assertThat(existing.getProcessingStatus()).isEqualTo("withdrawal-processing");
        assertThat(existing.getEtag()).isNotBlank();
        assertThat(existing.getProcessingStatusChangedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void updateWithdrawalProcessingStatus_whenSameStatus_returnsWithoutSaving() {
        WithdrawalDocument existing = buildSavedDocument();
        existing.setProcessingStatus("withdrawal-requested");

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(existing));

        strikeOffPartnerWithdrawalsService
                .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID,
                        createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED));

        verify(withdrawalRepository).findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID);
    }

    @Test
    void updateWithdrawalProcessingStatus_whenWithdrawalMissing_throwsNotFound() {
        UpdateWithdrawalStatusRequest request = createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);

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
        WithdrawalDocument existing = buildSavedDocument();
        existing.setProcessingStatus("withdrawal-requested");
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();

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
        WithdrawalDocument existing = buildSavedDocument();
        existing.setWithdrawalId(WITHDRAWAL_ID);
        existing.setProcessingStatus("withdrawal-requested");
        DataAccessResourceFailureException cause =
                new DataAccessResourceFailureException("mongo update failed");
        UpdateWithdrawalStatusRequest request = createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(existing));
        when(withdrawalRepository.updateProcessingStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), eq("withdrawal-requested"),
                eq("withdrawal-processing"), any(), anyString())).thenThrow(cause);

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService
                .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(WithdrawalPersistenceException.class)
                .hasMessage("Failed to persist updated withdrawal processing status")
                .hasCause(cause);
     }

     @ParameterizedTest
     @ValueSource(strings = {"different-organisation", "other-org", "wrong-partner"})
     void getWithdrawal_whenPartnerOrganisationDoesNotMatch_throwsForbidden(String wrongOrganisation) {
         WithdrawalDocument document = buildSavedDocument();
         document.setPartnerOrganisation(wrongOrganisation);

         when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                 .thenReturn(Optional.of(document));

         assertThatThrownBy(() ->
                 strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID, PARTNER_ORGANISATION))
                 .isInstanceOf(ResponseStatusException.class)
                 .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

         verifyNoInteractions(withdrawalMapper);
     }

      @ParameterizedTest(name = "Invalid status: {0}")
      @ValueSource(strings = {"unknown-status-value", "invalid-status"})
      void updateWithdrawalProcessingStatus_whenCurrentStatusIsInvalid_throwsConflict(String invalidStatus) {
          WithdrawalDocument existing = buildSavedDocument();
          existing.setProcessingStatus(invalidStatus);
          UpdateWithdrawalStatusRequest request = createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);

          when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                  .thenReturn(Optional.of(existing));

          assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService
                  .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request))
                  .isInstanceOf(ResponseStatusException.class)
                  .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
      }

      @Test
      void updateWithdrawalProcessingStatus_whenCurrentStatusIsNull_throwsConflict() {
          WithdrawalDocument existing = buildSavedDocument();
          existing.setProcessingStatus(null);
          UpdateWithdrawalStatusRequest request = createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);

          when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                  .thenReturn(Optional.of(existing));

          assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService
                  .updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request))
                  .isInstanceOf(ResponseStatusException.class)
                  .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
      }

    @ParameterizedTest
    @ValueSource(strings = {
            "withdrawal-accepted",
            "withdrawal-rejected"
    })
    void updateWithdrawalProcessingStatus_whenTerminalCallbackFails_persistsFailure(String targetStatusValue) {

        WithdrawalProcessingStatus targetStatus = WithdrawalProcessingStatus.fromValue(targetStatusValue);

        WithdrawalDocument existing = buildSavedDocument();
        existing.setProcessingStatus("withdrawal-processing");

        setupUpdateStatusMocks(existing);
        when(withdrawalRepository.updateCallbackStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), any(), any(), any(), any()))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        when(hmrcCallbackService.sendWithdrawalOutcomeCallback(
                WITHDRAWAL_ID,
                COMPANY_NUMBER,
                withdrawalCallbackUri()))
                .thenReturn(
                        CallbackResult.failure("Connection timeout"));

        UpdateWithdrawalStatusRequest request = createUpdateWithdrawalStatusRequest(targetStatus);
        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                        COMPANY_NUMBER,
                        WITHDRAWAL_ID,
                        request))
                .isInstanceOf(HmrcCallbackFailedException.class)
                .hasMessageContaining("Connection timeout");

        assertThat(existing.getCallbackStatus()).isEqualTo(CallbackStatus.FAILED);

        assertThat(existing.getCallbackFailureReason()).isEqualTo("Connection timeout");

        verify(withdrawalRepository).updateProcessingStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), eq("withdrawal-processing"),
                eq(targetStatusValue), any(), anyString());
        verify(withdrawalRepository).updateCallbackStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), eq(CallbackStatus.FAILED), any(), eq(null),
                eq("Connection timeout"));
    }

    @Test
    void updateWithdrawalProcessingStatus_whenCallbackUpdateResultIsNull_throwsPersistenceException() {
        WithdrawalDocument existing = stubTerminalStatusUpdate();
        when(withdrawalRepository.updateCallbackStatus(
                anyString(), anyString(), any(), any(), anyString(), eq(null)))
                .thenReturn(null);
        UpdateWithdrawalStatusRequest request =
                createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_ACCEPTED);

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(WithdrawalPersistenceException.class)
                .hasMessage("Failed to persist HMRC callback outcome for withdrawal");
        assertThat(existing.getCallbackStatus()).isEqualTo(CallbackStatus.SUCCESS);
    }

    @Test
    void updateWithdrawalProcessingStatus_whenCallbackUpdateIsUnacknowledged_throwsPersistenceException() {
        stubTerminalStatusUpdate();
        when(withdrawalRepository.updateCallbackStatus(
                anyString(), anyString(), any(), any(), anyString(), eq(null)))
                .thenReturn(UpdateResult.unacknowledged());
        UpdateWithdrawalStatusRequest request =
                createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_ACCEPTED);

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(WithdrawalPersistenceException.class)
                .hasMessage("Failed to persist HMRC callback outcome for withdrawal");
    }

    @Test
    void updateWithdrawalProcessingStatus_whenCallbackUpdateDoesNotMatchDocument_throwsPersistenceException() {
        stubTerminalStatusUpdate();
        when(withdrawalRepository.updateCallbackStatus(
                anyString(), anyString(), any(), any(), anyString(), eq(null)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        UpdateWithdrawalStatusRequest request =
                createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_ACCEPTED);

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(WithdrawalPersistenceException.class)
                .hasMessage("Failed to persist HMRC callback outcome for withdrawal");
    }

    @Test
    void updateWithdrawalProcessingStatus_whenCallbackUpdateThrowsDataAccessException_wrapsFailure() {
        stubTerminalStatusUpdate();
        DataAccessResourceFailureException cause =
                new DataAccessResourceFailureException("mongo update failed");
        when(withdrawalRepository.updateCallbackStatus(
                anyString(), anyString(), any(), any(), anyString(), eq(null)))
                .thenThrow(cause);
        UpdateWithdrawalStatusRequest request =
                createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus.WITHDRAWAL_ACCEPTED);

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(WithdrawalPersistenceException.class)
                .hasMessage("Failed to persist HMRC callback outcome for withdrawal")
                .hasCause(cause);
    }

    @Test
    void updateWithdrawalProcessingStatus_whenStatusUnchangedAndCallbackFailed_retriesCallback() {

        WithdrawalDocument existing = buildSavedDocument();
        existing.setWithdrawalId(WITHDRAWAL_ID);
        existing.setProcessingStatus("withdrawal-accepted");
        existing.setCallbackStatus(CallbackStatus.FAILED);

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                .thenReturn(Optional.of(existing));

        when(hmrcCallbackService.sendWithdrawalOutcomeCallback(
                WITHDRAWAL_ID,
                COMPANY_NUMBER,
                withdrawalCallbackUri()))
                .thenReturn(
                        CallbackResult.success("correlation-123"));
        when(withdrawalRepository.updateCallbackStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), any(), any(), anyString(), any()))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                COMPANY_NUMBER,
                WITHDRAWAL_ID,
                createUpdateWithdrawalStatusRequest(
                        WithdrawalProcessingStatus.WITHDRAWAL_ACCEPTED));

        verify(hmrcCallbackService).sendWithdrawalOutcomeCallback(
                WITHDRAWAL_ID,
                COMPANY_NUMBER,
                withdrawalCallbackUri());

        verify(withdrawalRepository).updateCallbackStatus(
                eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), eq(CallbackStatus.SUCCESS), any(),
                eq("correlation-123"), eq(null));
    }

    @Test
    void updateWithdrawalProcessingStatus_whenStatusUnchangedAndCallbackSuccessful_doesNothing() {

        WithdrawalDocument existing = buildSavedDocument();
        existing.setProcessingStatus("withdrawal-accepted");
        existing.setCallbackStatus(CallbackStatus.SUCCESS);

        when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(
                COMPANY_NUMBER,
                WITHDRAWAL_ID))
                .thenReturn(Optional.of(existing));

        strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                COMPANY_NUMBER,
                WITHDRAWAL_ID,
                createUpdateWithdrawalStatusRequest(
                        WithdrawalProcessingStatus.WITHDRAWAL_ACCEPTED));

        verifyNoInteractions(hmrcCallbackService);
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

       private UpdateWithdrawalStatusRequest createUpdateWithdrawalStatusRequest(WithdrawalProcessingStatus status) {
           UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();
           request.setProcessingStatus(status);
           return request;
       }


    private void setupUpdateStatusMocks(WithdrawalDocument existing) {
            existing.setWithdrawalId(WITHDRAWAL_ID);
            when(withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID))
                    .thenReturn(Optional.of(existing));
            when(withdrawalRepository.updateProcessingStatus(
                    eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), eq(existing.getProcessingStatus()),
                    anyString(), any(), anyString())).thenAnswer(invocation -> {
                        existing.setProcessingStatus(invocation.getArgument(3));
                        existing.setProcessingStatusChangedAt(invocation.getArgument(4));
                        existing.setEtag(invocation.getArgument(5));
                        return Optional.of(existing);
                    });
        }

    private WithdrawalDocument stubWithdrawalCreation() {
        WithdrawalDocument document = buildSavedDocument();
        document.setWithdrawalId(WITHDRAWAL_ID);
        when(withdrawalMapper.toWithdrawalDocument(any(), eq(COMPANY_NUMBER), eq(PARTNER_ORGANISATION), any(), any()))
                .thenReturn(document);
        when(withdrawalRepository.insert(document)).thenReturn(document);
        when(withdrawalKafkaProducer.publishWithdrawalEvent(document)).thenReturn(getPublishedEvent("event-id"));
        when(withdrawalMapper.toWithdrawAllObjectionsResponse(document)).thenReturn(buildResponse(document));
        return document;
    }

    private WithdrawalDocument stubTerminalStatusUpdate() {
        WithdrawalDocument document = buildSavedDocument();
        document.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING.getValue());
        setupUpdateStatusMocks(document);
        when(hmrcCallbackService.sendWithdrawalOutcomeCallback(
                eq(WITHDRAWAL_ID), eq(COMPANY_NUMBER), anyString()))
                .thenReturn(CallbackResult.success("callback-id"));
        return document;
    }

        private String withdrawalCallbackUri() {
            return String.format(
                    "/company/%s/strike-off-partner-objections-withdrawals/%s",
                    COMPANY_NUMBER, WITHDRAWAL_ID);
        }

 }
