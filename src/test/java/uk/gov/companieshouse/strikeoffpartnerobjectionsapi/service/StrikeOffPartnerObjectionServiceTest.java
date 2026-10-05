package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.UpdateObjectionStatusRequest;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.ObjectionKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionRequestMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionResponseMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;


@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerObjectionServiceTest {

    @Mock
    private ObjectionRepository objectionRepository;

    @Mock
    private ObjectionRequestMapper objectionRequestMapper;

    @Mock
    private ObjectionResponseMapper objectionResponseMapper;
    
    @Mock
    private ObjectionKafkaProducer objectionKafkaProducer;

    @Mock
    private CompanyValidator companyValidator;

    @Mock
    private HmrcCallbackService hmrcCallbackService;

    private static final String VALID_COMPANY_NUMBER = "12345";
    private static final String VALID_OBJECTION_ID = "objection-1";
    private static final String ANOTHER_COMPANY_NUMBER = "1";
    private static final String ANOTHER_OBJECTION_ID = "2";
    private static final String TEST_COMPANY_NAME = "Test Company Ltd";
    private static final String CALLBACK_SUCCESS_OBJECTION_ID = "objection-callback-success";
    private static final String CALLBACK_FAILURE_OBJECTION_ID = "objection-callback-failure";
    private static final String CALLBACK_SAVE_FAIL_OBJECTION_ID = "objection-callback-save-fail";
    private static final String CALLBACK_NOT_FOUND_OBJECTION_ID = "objection-callback-not-found";
    private static final String NULL_STATUS_OBJECTION_ID = "objection-null-status";
    private static final String MISSING_OBJECTION_ID = "missing-objection";
    private static final String API_SOURCE = "strike-off-partner-objections-api";
    private static final String CALLBACK_URI_FORMAT = "/company/%s/strike-off/objections/%s";
    private static final int HTTP_STATUS_CONFLICT = 409;
    private static final int HTTP_STATUS_FORBIDDEN = 403;
    private static final int HTTP_STATUS_INTERNAL_SERVER_ERROR = 500;

    private StrikeOffPartnerObjectionService strikeOffPartnerObjectionService;
    private CreateObjectionRequest requestDto;
    private ObjectionDocument mappedDocument;
    private ObjectionDocument savedDocument;

    @BeforeEach
    void setUp() {
        strikeOffPartnerObjectionService = new StrikeOffPartnerObjectionService(
                objectionRepository,
                objectionRequestMapper,
                objectionResponseMapper,
                objectionKafkaProducer,
                companyValidator,
                hmrcCallbackService
        );
        requestDto = validCreateObjectionRequest();
        mappedDocument = new ObjectionDocument();
        savedDocument = new ObjectionDocument();
    }

    @Test
    void createObjection_whenRequestIsValid_returnsMappedResponse() {
        String companyNumber = VALID_COMPANY_NUMBER;

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenReturn(savedDocument);
        BaseObjectionResponse expectedResponse = new BaseObjectionResponse();
        when(objectionResponseMapper.toObjectionApiResponse(savedDocument)).thenReturn(expectedResponse);
        when(objectionKafkaProducer.publishObjectionEvent(savedDocument)).thenReturn(getPublishedEvent("event-id-1"));


        BaseObjectionResponse result = strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto, PARTNER_ORGANISATION);

        assertThat(result).isSameAs(expectedResponse);
        verify(objectionRequestMapper).toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString());
        verify(objectionRepository).insert(mappedDocument);
        ArgumentCaptor<ObjectionDocument> savedCaptor = ArgumentCaptor.forClass(ObjectionDocument.class);
        verify(objectionRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getEventStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(savedCaptor.getValue().getEventCorrelationId()).isNotBlank();
        verify(objectionResponseMapper).toObjectionApiResponse(savedDocument);
        verify(objectionKafkaProducer).publishObjectionEvent(savedDocument);
    }

    @Test
    void createObjection_whenKafkaPublishFails_marksEventAsFailedAndRethrows() {
        String companyNumber = VALID_COMPANY_NUMBER;
        KafkaPublishException kafkaException =
                new KafkaPublishException("publish failed", "event-id-2", new RuntimeException("boom"));

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(objectionKafkaProducer.publishObjectionEvent(savedDocument)).thenThrow(kafkaException);

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto, PARTNER_ORGANISATION))
                .isSameAs(kafkaException);

        ArgumentCaptor<ObjectionDocument> savedCaptor = ArgumentCaptor.forClass(ObjectionDocument.class);
        verify(objectionRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getEventStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(savedCaptor.getValue().getEventFailureReason()).contains("publish failed");
        assertThat(savedCaptor.getValue().getEventCorrelationId()).isNotBlank();
    }

    @Test
    void createObjection_whenRepositoryInsertFails_throwsException() {
        String companyNumber = VALID_COMPANY_NUMBER;
        DataAccessResourceFailureException cause =
                new DataAccessResourceFailureException("mongo insert failed");


        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenThrow(cause);

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto, PARTNER_ORGANISATION))
                .isInstanceOf(ObjectionPersistenceException.class)
                .hasMessage("Failed to persist objection")
                .hasCause(cause);

        verify(objectionRequestMapper).toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString());
        verify(objectionRepository).insert(mappedDocument);
        verifyNoInteractions(objectionResponseMapper);
    }

    @Test
    void createObjection_whenCalledMultipleTimes_generatesUniqueObjectionId() {
        String companyNumber = VALID_COMPANY_NUMBER;

        ArgumentCaptor<String> objectionIdCaptor = ArgumentCaptor.forClass(String.class);

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), objectionIdCaptor.capture()))
                .thenReturn(new ObjectionDocument());
        when(objectionRepository.insert(any(ObjectionDocument.class))).thenReturn(new ObjectionDocument());
        when(objectionKafkaProducer.publishObjectionEvent(any(ObjectionDocument.class))).thenReturn(getPublishedEvent("event-id-3"));
        when(objectionResponseMapper.toObjectionApiResponse(any())).thenReturn(new BaseObjectionResponse());

        strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto, PARTNER_ORGANISATION);
        strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto, PARTNER_ORGANISATION);

        assertThat(objectionIdCaptor.getAllValues()).hasSize(2);
        assertThat(objectionIdCaptor.getAllValues().get(0)).isNotBlank();
        assertThat(objectionIdCaptor.getAllValues().get(1)).isNotBlank();
        assertThat(objectionIdCaptor.getAllValues().get(0)).isNotEqualTo(objectionIdCaptor.getAllValues().get(1));
    }

    @Test
    void createObjection_whenSaveAfterPublishFails_stillReturnsSuccessResponse() {
        String companyNumber = VALID_COMPANY_NUMBER;
        BaseObjectionResponse expectedResponse = new BaseObjectionResponse();
        DataAccessResourceFailureException saveException =
                new DataAccessResourceFailureException("mongo update failed");

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(objectionKafkaProducer.publishObjectionEvent(savedDocument)).thenReturn(getPublishedEvent("event-id-4"));
        when(objectionRepository.save(any(ObjectionDocument.class))).thenThrow(saveException);
        when(objectionResponseMapper.toObjectionApiResponse(savedDocument)).thenReturn(expectedResponse);

        BaseObjectionResponse result = strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto, PARTNER_ORGANISATION);

        assertThat(result).isSameAs(expectedResponse);
        verify(objectionRepository).save(any(ObjectionDocument.class));
    }

    @Test
    void createObjection_whenKafkaFailsAndSaveAfterFailureThrows_stillRethrowsOriginalKafkaException() {
        String companyNumber = VALID_COMPANY_NUMBER;
        KafkaPublishException kafkaException =
                new KafkaPublishException("publish failed", "event-id-5", new RuntimeException("boom"));
        DataAccessResourceFailureException saveException =
                new DataAccessResourceFailureException("mongo update failed");

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(objectionKafkaProducer.publishObjectionEvent(savedDocument)).thenThrow(kafkaException);
        when(objectionRepository.save(any(ObjectionDocument.class))).thenThrow(saveException);

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto, PARTNER_ORGANISATION))
                .isSameAs(kafkaException);

        verify(objectionRepository).save(any(ObjectionDocument.class));
    }

    @Test
    void getObjection_whenObjectionExists_returnsObjection() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument document = new ObjectionDocument();
        document.setPartnerOrganisation(PARTNER_ORGANISATION);
        BaseObjectionResponse expectedResponse = new BaseObjectionResponse();

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
        .thenReturn(Optional.of(document));
        when(objectionResponseMapper.toObjectionApiResponse(document)).thenReturn(expectedResponse);

        BaseObjectionResponse result = strikeOffPartnerObjectionService.getObjection(companyNumber, objectionId, PARTNER_ORGANISATION);

        assertEquals(expectedResponse, result);
        verify(objectionRepository).findByCompanyNumberAndObjectionId(companyNumber, objectionId);
        verify(objectionResponseMapper).toObjectionApiResponse(document);
    }

    @Test
    void getObjection_whenObjectionDoesNotExist_throwsObjectionNotFoundExceptionWithMessage() {
        String companyNumber = ANOTHER_COMPANY_NUMBER;
        String objectionId = ANOTHER_OBJECTION_ID;

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
                .thenReturn(Optional.empty());
        ObjectionNotFoundException ex = assertThrows(
                ObjectionNotFoundException.class,
                () -> strikeOffPartnerObjectionService.getObjection(companyNumber, objectionId, PARTNER_ORGANISATION));
        assertEquals(
                format("Objection not found for company number=%s, objectionId=%s", companyNumber, objectionId),
                ex.getMessage());
        verify(objectionRepository).findByCompanyNumberAndObjectionId(companyNumber, objectionId);
        verifyNoInteractions(objectionResponseMapper);
    }

    @Test
    void createObjection_callsCompanyValidator() {
        String companyNumber = VALID_COMPANY_NUMBER;
        BaseObjectionResponse response = new BaseObjectionResponse();

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(objectionKafkaProducer.publishObjectionEvent(savedDocument)).thenReturn(getPublishedEvent("event-id-6"));
        when(objectionResponseMapper.toObjectionApiResponse(savedDocument)).thenReturn(response);

        strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto, PARTNER_ORGANISATION);

        verify(companyValidator, times(1)).validateCompany(companyNumber, requestDto.getSubmissionCompanyName());
    }

    @Test
    void createObjection_whenValidatorReturnsInvalid_doesNotProceed() {
        String companyNumber = VALID_COMPANY_NUMBER;
        CompanyValidationException validationException =
                new CompanyValidationException("Company not found", "COMPANY_NUMBER_NOT_EXIST");

        doThrow(validationException).when(companyValidator).validateCompany(companyNumber, requestDto.getSubmissionCompanyName());

        assertThatThrownBy(() ->
                strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto, PARTNER_ORGANISATION))
                .isSameAs(validationException);

        verify(companyValidator).validateCompany(companyNumber, requestDto.getSubmissionCompanyName());
        verifyNoInteractions(objectionRequestMapper, objectionRepository, objectionKafkaProducer);
    }

    @ParameterizedTest
    @CsvSource({
        "objection-submitted,objection-processing",
        "objection-processing,objection-accepted",
        "objection-processing,objection-rejected"
    })
    void updateObjectionProcessingStatus_whenValidTransition_updatesStatus(String currentStatus, String newStatus) {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionProcessingStatus targetStatus = ObjectionProcessingStatus.fromValue(newStatus);
        ObjectionDocument existing = createObjectionDocument(currentStatus, objectionId, companyNumber);

        setupUpdateStatusMocks(companyNumber, objectionId, existing, "etag-transition");

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId, createUpdateStatusRequest(targetStatus));

        ObjectionDocument savedDoc = captureAndVerifySavedDocument();
        assertThat(savedDoc.getProcessingStatus()).isEqualTo(newStatus);
    }

    @Test
    void updateObjectionProcessingStatus_whenAlreadyProcessing_returnsWithoutSaving() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument existing = createObjectionDocument("objection-processing");

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
        .thenReturn(Optional.of(existing));

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId,
                createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING));

        verify(objectionRepository).findByCompanyNumberAndObjectionId(companyNumber, objectionId);
        verify(objectionRepository, never()).save(any(ObjectionDocument.class));
        verifyNoInteractions(objectionRequestMapper);
    }

    @ParameterizedTest
    @CsvSource({
        "objection-rejected,objection-processing",
        "objection-accepted,objection-processing",
        "objection-accepted,objection-rejected",
        "objection-rejected,objection-accepted",
        "objection-submitted,objection-accepted"
    })
    void updateObjectionProcessingStatus_whenInvalidTransition_throwsConflict(String currentStatus, String targetStatusStr) {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionProcessingStatus targetStatus = ObjectionProcessingStatus.fromValue(targetStatusStr);
        ObjectionDocument existing = createObjectionDocument(currentStatus);
        UpdateObjectionStatusRequest request = createUpdateStatusRequest(targetStatus);

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(HTTP_STATUS_CONFLICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "unsupported-status", "invalid"})
    void parseRequestedStatus_whenStatusIsInvalid_throwsBadRequest(String invalidStatus) {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(strikeOffPartnerObjectionService, "parseRequestedStatus", invalidStatus));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Unsupported status");
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void parseRequestedStatus_whenStatusIsNull_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(strikeOffPartnerObjectionService, "parseRequestedStatus", (String) null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"different-organisation", "other-org", "wrong-partner"})
    void getObjection_whenPartnerOrganisationDoesNotMatch_throwsForbidden(String wrongOrganisation) {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument document = new ObjectionDocument();
        document.setPartnerOrganisation(wrongOrganisation);

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
        .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.getObjection(companyNumber, objectionId, PARTNER_ORGANISATION))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(HTTP_STATUS_FORBIDDEN);

        verify(objectionRepository).findByCompanyNumberAndObjectionId(companyNumber, objectionId);
        verifyNoInteractions(objectionResponseMapper);
    }

    @Test
    void updateObjectionProcessingStatus_whenObjectionNotFound_throwsObjectionNotFoundException() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = MISSING_OBJECTION_ID;
        UpdateObjectionStatusRequest request = createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING);

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
        .thenReturn(Optional.empty());

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId, request))
                .isInstanceOf(ObjectionNotFoundException.class)
                .hasMessageContaining(companyNumber)
                .hasMessageContaining(objectionId);
    }

    @Test
    void updateObjectionProcessingStatus_whenRepositorySaveFails_throwsObjectionPersistenceException() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument existing = createObjectionDocument("objection-submitted");
        DataAccessResourceFailureException cause = new DataAccessResourceFailureException("mongo update failed");
        UpdateObjectionStatusRequest request = createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING);

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
        .thenReturn(Optional.of(existing));
        when(objectionRequestMapper.getEtag()).thenReturn("etag-save-fail");
        when(objectionRepository.save(any(ObjectionDocument.class))).thenThrow(cause);

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId, request))
                .isInstanceOf(ObjectionPersistenceException.class)
                .hasMessage("Failed to persist updated objection processing status")
                .hasCause(cause);
    }

    @Test
    void parseCurrentStatus_whenCurrentStatusIsInvalid_throwsInternalServerError() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument existing = createObjectionDocument("unknown-status-value");
        UpdateObjectionStatusRequest request = createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING);

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
        .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(HTTP_STATUS_INTERNAL_SERVER_ERROR);
    }

    @Test
    void updateObjectionProcessingStatus_whenSuccessful_triggersHmrcCallback() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument existing = createObjectionDocument("objection-submitted", objectionId, companyNumber);

        setupUpdateStatusMocks(companyNumber, objectionId, existing, "etag-2");

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId,
                createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING));

         ArgumentCaptor<String> callbackIdCaptor = ArgumentCaptor.forClass(String.class);
         verify(hmrcCallbackService).sendObjectionOutcomeCallback(
                 eq(objectionId),
                 eq(companyNumber),
                 callbackIdCaptor.capture(),
                 ArgumentMatchers.any());

        String callbackUri = callbackIdCaptor.getValue();
        assertEquals(format(CALLBACK_URI_FORMAT, companyNumber, objectionId), callbackUri);
    }

    @Test
    void updateObjectionProcessingStatus_whenTransitionToAccepted_triggersCallbackWithCorrectUri() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument existing = createObjectionDocument("objection-processing", objectionId, companyNumber);

        setupUpdateStatusMocks(companyNumber, objectionId, existing, "etag-3");

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId,
                createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_ACCEPTED));

         verify(hmrcCallbackService).sendObjectionOutcomeCallback(
                 eq(objectionId),
                 eq(companyNumber),
                 eq(format(CALLBACK_URI_FORMAT, companyNumber, objectionId)),
                 ArgumentMatchers.any());
    }

    @Test
    void updateObjectionProcessingStatus_whenCallbackFails_doesNotBlockResponse() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument existing = createObjectionDocument("objection-submitted", objectionId, companyNumber);

        setupUpdateStatusMocks(companyNumber, objectionId, existing, "etag-2");

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId,
                createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING));

         verify(objectionRepository).save(any(ObjectionDocument.class));
         verify(hmrcCallbackService).sendObjectionOutcomeCallback(
                 eq(objectionId),
                 eq(companyNumber),
                 anyString(),
                 ArgumentMatchers.any());
    }

      @Test
      void updateObjectionProcessingStatus_whenAlreadyProcessing_noCallbackTriggered() {
          String companyNumber = VALID_COMPANY_NUMBER;
          String objectionId = VALID_OBJECTION_ID;
          ObjectionDocument existing = createObjectionDocument("objection-processing");

          when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
          .thenReturn(Optional.of(existing));

          strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                  companyNumber, objectionId,
                  createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING));

          verify(objectionRepository).findByCompanyNumberAndObjectionId(companyNumber, objectionId);
          verify(objectionRepository, never()).save(any(ObjectionDocument.class));
          verifyNoInteractions(hmrcCallbackService);
      }

      @Test
      void updateObjectionProcessingStatus_callbackResultHandlerSuccessPath() {
          String companyNumber = VALID_COMPANY_NUMBER;
          String objectionId = CALLBACK_SUCCESS_OBJECTION_ID;
          ObjectionDocument existing = createObjectionDocument("objection-submitted", objectionId, companyNumber);

          setupUpdateStatusMocks(companyNumber, objectionId, existing, "etag-callback-test");

          strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                  companyNumber, objectionId,
                  createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING));

          Consumer<CallbackResult> handler = captureCallbackHandler();

          // Test the result handler with success scenario
          handler.accept(new CallbackResult("correlation-123", 1));

          ArgumentCaptor<ObjectionDocument> documentCaptor = ArgumentCaptor.forClass(ObjectionDocument.class);
          verify(objectionRepository, times(2)).save(documentCaptor.capture());

          ObjectionDocument savedDoc = documentCaptor.getAllValues().get(1);
          assertThat(savedDoc.getCallbackCorrelationId()).isEqualTo("correlation-123");
      }

      @Test
      void updateObjectionProcessingStatus_callbackResultHandlerFailurePath() {
          String companyNumber = VALID_COMPANY_NUMBER;
          String objectionId = CALLBACK_FAILURE_OBJECTION_ID;
          ObjectionDocument existing = createObjectionDocument("objection-processing", objectionId, companyNumber);

         setupUpdateStatusMocks(companyNumber, objectionId, existing, "etag-failure-test");

         strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                 companyNumber, objectionId,
                 createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_ACCEPTED));

         Consumer<CallbackResult> handler = captureCallbackHandler();

         // Test the result handler with failure scenario
         handler.accept(new CallbackResult(3, "Connection timeout"));

          ArgumentCaptor<ObjectionDocument> documentCaptor = ArgumentCaptor.forClass(ObjectionDocument.class);
          verify(objectionRepository, times(2)).save(documentCaptor.capture());

          ObjectionDocument savedDoc = documentCaptor.getAllValues().get(1);
          assertThat(savedDoc.getCallbackCorrelationId()).isNull();
      }

      @Test
      void updateObjectionProcessingStatus_callbackResultHandlerPersistenceFails() {
          String companyNumber = VALID_COMPANY_NUMBER;
          String objectionId = CALLBACK_SAVE_FAIL_OBJECTION_ID;
          ObjectionDocument existing = createObjectionDocument("objection-processing", objectionId, companyNumber);

         setupUpdateStatusMocks(companyNumber, objectionId, existing, "etag-persistence-test");

         strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                 companyNumber, objectionId,
                 createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_REJECTED));

          Consumer<CallbackResult> handler = captureCallbackHandler();

          // Test the result handler when persistence fails - exception should propagate
          when(objectionRepository.save(any(ObjectionDocument.class)))
                  .thenThrow(new DataAccessResourceFailureException("Save failed"));

          // Handler should throw exception so HmrcCallbackService can detect failure
          CallbackResult callbackResult = new CallbackResult("correlation-persist", 1);
          assertThatThrownBy(() -> handler.accept(callbackResult))
                  .isInstanceOf(ObjectionPersistenceException.class)
                  .hasMessageContaining("Failed to persist callback status after");
       }

      @Test
      void updateObjectionProcessingStatus_callbackResultHandlerObjectionNotFound() {
          String companyNumber = VALID_COMPANY_NUMBER;
          String objectionId = CALLBACK_NOT_FOUND_OBJECTION_ID;
          ObjectionDocument existing = createObjectionDocument("objection-processing", objectionId, companyNumber);

          setupUpdateStatusMocks(companyNumber, objectionId, existing, "etag-not-found-test");

          strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                  companyNumber, objectionId,
                  createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_REJECTED));

          Consumer<CallbackResult> handler = captureCallbackHandler();

          // Test the result handler when objection is not found - exception should propagate
          when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
                  .thenReturn(Optional.empty());

          // Handler should throw exception so HmrcCallbackService can detect failure
          CallbackResult callbackResultNotFound = new CallbackResult("correlation-not-found", 1);
          assertThatThrownBy(() -> handler.accept(callbackResultNotFound))
                  .isInstanceOf(ObjectionNotFoundException.class)
                  .hasMessageContaining("Objection not found");
       }

      @Test
      void parseCurrentStatus_whenStatusIsNull_throwsInternalServerError() {
          String companyNumber = VALID_COMPANY_NUMBER;
          String objectionId = NULL_STATUS_OBJECTION_ID;
          ObjectionDocument existing = createObjectionDocument(null);
          UpdateObjectionStatusRequest request = createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING);

          when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
          .thenReturn(Optional.of(existing));

          assertThatThrownBy(() -> strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                  companyNumber, objectionId, request))
                  .isInstanceOf(ResponseStatusException.class)
                  .extracting("statusCode.value")
                  .isEqualTo(HTTP_STATUS_INTERNAL_SERVER_ERROR);
      }

      @Test
      void updateObjectionProcessingStatus_trims_requestedStatusValue() {
         String companyNumber = VALID_COMPANY_NUMBER;
         String objectionId = VALID_OBJECTION_ID;
         ObjectionDocument existing = createObjectionDocument("objection-submitted", objectionId, companyNumber);

         setupUpdateStatusMocks(companyNumber, objectionId, existing, "etag-trim");

         strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                 companyNumber, objectionId,
                 createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING));

         ObjectionDocument savedDoc = captureAndVerifySavedDocument();
         assertThat(savedDoc.getProcessingStatus()).isEqualTo("objection-processing");
     }

      private StrikeOffPartnerObjections getPublishedEvent(String eventId) {
          return StrikeOffPartnerObjections.newBuilder()
                  .setEventId(eventId)
                  .setEventType(EventType.OBJECTION)
                  .setEventTime(java.time.Instant.now().toString())
                  .setSource(API_SOURCE)
                  .setCompanyNumber(VALID_COMPANY_NUMBER)
                  .setPartnerOrganisation(PARTNER_ORGANISATION)
                  .setStrikeOffEventId(UUID.randomUUID().toString())
                  .build();
      }

      private CreateObjectionRequest validCreateObjectionRequest() {
          CreateObjectionRequest request = new CreateObjectionRequest();
          request.setSubmissionCompanyName(TEST_COMPANY_NAME);
          return request;
      }

     private UpdateObjectionStatusRequest createUpdateStatusRequest(ObjectionProcessingStatus status) {
         UpdateObjectionStatusRequest request = new UpdateObjectionStatusRequest();
         request.setProcessingStatus(status);
         return request;
     }

     private ObjectionDocument createObjectionDocument(String currentStatus, String objectionId, String companyNumber) {
         ObjectionDocument document = new ObjectionDocument();
         document.setProcessingStatus(currentStatus);
         document.setObjectionId(objectionId);
         document.setCompanyNumber(companyNumber);
         return document;
     }

     private ObjectionDocument createObjectionDocument(String currentStatus) {
         ObjectionDocument document = new ObjectionDocument();
         document.setProcessingStatus(currentStatus);
         return document;
     }

     private void setupUpdateStatusMocks(String companyNumber, String objectionId, ObjectionDocument existing, String etag) {
         when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
                 .thenReturn(Optional.of(existing));
         when(objectionRequestMapper.getEtag()).thenReturn(etag);
         when(objectionRepository.save(any(ObjectionDocument.class))).thenReturn(existing);
     }

     private ObjectionDocument captureAndVerifySavedDocument() {
         ArgumentCaptor<ObjectionDocument> captor = ArgumentCaptor.forClass(ObjectionDocument.class);
         verify(objectionRepository).save(captor.capture());
         return captor.getValue();
     }

     @SuppressWarnings("unchecked")
     private Consumer<CallbackResult> captureCallbackHandler() {
         ArgumentCaptor<Consumer<CallbackResult>> handlerCaptor =
                 ArgumentCaptor.forClass(Consumer.class);
         verify(hmrcCallbackService).sendObjectionOutcomeCallback(
                 anyString(),
                 anyString(),
                 anyString(),
                 handlerCaptor.capture());
         return handlerCaptor.getValue();
     }
 }
