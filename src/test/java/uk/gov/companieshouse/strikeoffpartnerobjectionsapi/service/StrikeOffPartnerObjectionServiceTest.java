package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.HmrcCallbackFailedException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.ObjectionKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionRequestMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionResponseMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;


@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerObjectionServiceTest {

    private static final String VALID_COMPANY_NUMBER = "12345";
    private static final String VALID_OBJECTION_ID = "objection-1";
    private static final String ANOTHER_COMPANY_NUMBER = "1";
    private static final String ANOTHER_OBJECTION_ID = "2";
    private static final String TEST_COMPANY_NAME = "Test Company Ltd";
    private static final String MISSING_OBJECTION_ID = "missing-objection";
    private static final String API_SOURCE = "strike-off-partner-objections-api";
    private static final String CALLBACK_URI_FORMAT = "/company/%s/strike-off-partner-objections/%s";
    private static final int HTTP_STATUS_CONFLICT = 409;
    private static final int HTTP_STATUS_FORBIDDEN = 403;
    private static final int HTTP_STATUS_INTERNAL_SERVER_ERROR = 500;
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

        setupUpdateStatusMocks(companyNumber, objectionId, existing);
        if (newStatus.equals("objection-accepted") || newStatus.equals("objection-rejected")) {
            mockSuccessfulCallback();
        }

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId, createUpdateStatusRequest(targetStatus));
        ArgumentCaptor<ObjectionDocument> captor =
                ArgumentCaptor.forClass(ObjectionDocument.class);

        verify(objectionRepository, atLeastOnce()).save(captor.capture());

        assertThat(captor.getValue().getProcessingStatus()).isEqualTo(newStatus);
        ObjectionDocument savedDoc = captor.getValue();
        assertThat(savedDoc.getProcessingStatus()).isEqualTo(newStatus);
        if (newStatus.equals("objection-accepted") || newStatus.equals("objection-rejected")) {
            verify(hmrcCallbackService).sendObjectionOutcomeCallback(
                    VALID_OBJECTION_ID,
                    VALID_COMPANY_NUMBER,
                    format(CALLBACK_URI_FORMAT, VALID_COMPANY_NUMBER, VALID_OBJECTION_ID));
        } else {
            verifyNoInteractions(hmrcCallbackService);
        }
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown-status-value"})
    void parseCurrentStatus_whenInvalid_throwsInternalServerError(String currentStatus)  {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument existing = createObjectionDocument(currentStatus);
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
    void updateObjectionProcessingStatus_whenStatusUnchangedAndCallbackFailed_retriesCallback() {
        ObjectionDocument existing = createObjectionDocument(
                "objection-accepted", VALID_OBJECTION_ID, VALID_COMPANY_NUMBER);
        existing.setCallbackStatus(CallbackStatus.FAILED);
        setupUpdateStatusMocks(
                VALID_COMPANY_NUMBER, VALID_OBJECTION_ID, existing);
        mockSuccessfulCallback();
        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                VALID_COMPANY_NUMBER,
                VALID_OBJECTION_ID,
                createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_ACCEPTED));

        verify(hmrcCallbackService).sendObjectionOutcomeCallback(
                VALID_OBJECTION_ID,
                VALID_COMPANY_NUMBER,
                format(CALLBACK_URI_FORMAT, VALID_COMPANY_NUMBER, VALID_OBJECTION_ID));
    }

    @Test
    void updateObjectionProcessingStatus_whenStatusUnchangedAndCallbackSuccessful_doesNothing() {
        ObjectionDocument existing = createObjectionDocument(
                "objection-accepted",
                VALID_OBJECTION_ID,
                VALID_COMPANY_NUMBER);

        existing.setCallbackStatus(CallbackStatus.SUCCESS);

        when(objectionRepository.findByCompanyNumberAndObjectionId(VALID_COMPANY_NUMBER, VALID_OBJECTION_ID))
                .thenReturn(Optional.of(existing));

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                VALID_COMPANY_NUMBER,
                VALID_OBJECTION_ID,
                createUpdateStatusRequest(
                        ObjectionProcessingStatus.OBJECTION_ACCEPTED));

        verifyNoInteractions(hmrcCallbackService);
    }

    @Test
    void updateObjectionProcessingStatus_whenCallbackSucceeds_persistsSuccess() {
        ObjectionDocument existing = createObjectionDocument(
                "objection-processing", VALID_OBJECTION_ID, VALID_COMPANY_NUMBER);
            setupUpdateStatusMocks(
                VALID_COMPANY_NUMBER, VALID_OBJECTION_ID, existing);
        mockSuccessfulCallback();
        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                VALID_COMPANY_NUMBER,
                VALID_OBJECTION_ID,
                createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_ACCEPTED));

        assertThat(existing.getProcessingStatus()).isEqualTo("objection-accepted");
        assertThat(existing.getCallbackStatus()).isEqualTo(CallbackStatus.SUCCESS);
        assertThat(existing.getCallbackCorrelationId()).isEqualTo("correlation-123");
        assertThat(existing.getCallbackFailureReason()).isNull();
        assertThat(existing.getCallbackStatusChangedAt()).isNotNull();

        InOrder order = inOrder(objectionRepository, hmrcCallbackService);
        order.verify(objectionRepository).findByCompanyNumberAndObjectionId(
                VALID_COMPANY_NUMBER, VALID_OBJECTION_ID);
        order.verify(objectionRepository).save(existing);
        order.verify(hmrcCallbackService).sendObjectionOutcomeCallback(
                VALID_OBJECTION_ID,
                VALID_COMPANY_NUMBER,
                format(CALLBACK_URI_FORMAT, VALID_COMPANY_NUMBER, VALID_OBJECTION_ID));
        order.verify(objectionRepository).save(existing);
        verifyNoMoreInteractions(hmrcCallbackService);
    }

    @Test
    void updateObjectionProcessingStatus_whenCallbackFails_persistsFailureBeforeThrowing() {
        ObjectionDocument existing = createObjectionDocument(
                "objection-processing", VALID_OBJECTION_ID, VALID_COMPANY_NUMBER);
        setupUpdateStatusMocks(
                VALID_COMPANY_NUMBER, VALID_OBJECTION_ID, existing);

        when(hmrcCallbackService.sendObjectionOutcomeCallback(
                VALID_OBJECTION_ID,
                VALID_COMPANY_NUMBER,
                format(CALLBACK_URI_FORMAT, VALID_COMPANY_NUMBER, VALID_OBJECTION_ID)))
                .thenReturn(CallbackResult.failure("Connection timeout"));

        UpdateObjectionStatusRequest request =
                createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_ACCEPTED);

        assertThatThrownBy(() ->
                strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                        VALID_COMPANY_NUMBER,
                        VALID_OBJECTION_ID,
                        request))
                .isInstanceOf(HmrcCallbackFailedException.class)
                .hasMessageContaining("Connection timeout");

        assertThat(existing.getProcessingStatus()).isEqualTo("objection-accepted");
        assertThat(existing.getCallbackStatus()).isEqualTo(CallbackStatus.FAILED);
        assertThat(existing.getCallbackFailureReason()).isEqualTo("Connection timeout");
        assertThat(existing.getCallbackCorrelationId()).isNull();
        assertThat(existing.getCallbackStatusChangedAt()).isNotNull();

        InOrder order = inOrder(objectionRepository, hmrcCallbackService);
        order.verify(objectionRepository).findByCompanyNumberAndObjectionId(
                VALID_COMPANY_NUMBER, VALID_OBJECTION_ID);
        order.verify(objectionRepository).save(existing);
        order.verify(hmrcCallbackService).sendObjectionOutcomeCallback(
                VALID_OBJECTION_ID,
                VALID_COMPANY_NUMBER,
                format(CALLBACK_URI_FORMAT, VALID_COMPANY_NUMBER, VALID_OBJECTION_ID));
        order.verify(objectionRepository).save(existing);
        verifyNoMoreInteractions(hmrcCallbackService);
    }

    @Test
    void updateObjectionProcessingStatus_trims_requestedStatusValue() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = VALID_OBJECTION_ID;
        ObjectionDocument existing = createObjectionDocument("objection-submitted", objectionId, companyNumber);

        setupUpdateStatusMocks(companyNumber, objectionId, existing);

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber, objectionId,
                createUpdateStatusRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING));
        ArgumentCaptor<ObjectionDocument> captor =
                ArgumentCaptor.forClass(ObjectionDocument.class);
        verify(objectionRepository, times(1)).save(captor.capture());
        ObjectionDocument savedDoc = captor.getValue();
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

    private void setupUpdateStatusMocks(
            String companyNumber,
            String objectionId,
            ObjectionDocument existing) {
        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId))
                .thenReturn(Optional.of(existing));
        when(objectionRepository.save(any(ObjectionDocument.class))).thenReturn(existing);
    }

    private String callbackUri() {
        return format(
                CALLBACK_URI_FORMAT,
                VALID_COMPANY_NUMBER,
                VALID_OBJECTION_ID);
    }

    private void mockSuccessfulCallback() {
        when(hmrcCallbackService.sendObjectionOutcomeCallback(
                VALID_OBJECTION_ID,
                VALID_COMPANY_NUMBER,
                callbackUri()))
                .thenReturn(
                        CallbackResult.success("correlation-123"));
    }
}
