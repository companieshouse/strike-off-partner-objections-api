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
import static org.mockito.Mockito.doThrow;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    
    private static final String VALID_COMPANY_NUMBER = "12345";

    private StrikeOffPartnerObjectionService strikeOffPartnerObjectionService;

    @BeforeEach
    void setUp() {
        strikeOffPartnerObjectionService = new StrikeOffPartnerObjectionService(
                objectionRepository,
                objectionRequestMapper,
                objectionResponseMapper,
                objectionKafkaProducer,
                companyValidator
        );
    }
    @Test
    void createObjection_whenRequestIsValid_returnsMappedResponse() {
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
        String companyNumber = VALID_COMPANY_NUMBER;

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenReturn(savedDocument);
        BaseObjectionResponse expectedResponse = new BaseObjectionResponse();
        when(objectionResponseMapper.toObjectionApiResponse(savedDocument)).thenReturn(expectedResponse);
        when(objectionKafkaProducer.publishObjectionEvent(savedDocument)).thenReturn(getPublishedEvent("event-id-1"));


        BaseObjectionResponse result = strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto);

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
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
        String companyNumber = VALID_COMPANY_NUMBER;
        KafkaPublishException kafkaException =
                new KafkaPublishException("publish failed", "event-id-2", new RuntimeException("boom"));

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(objectionKafkaProducer.publishObjectionEvent(savedDocument)).thenThrow(kafkaException);

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto))
                .isSameAs(kafkaException);

        ArgumentCaptor<ObjectionDocument> savedCaptor = ArgumentCaptor.forClass(ObjectionDocument.class);
        verify(objectionRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getEventStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(savedCaptor.getValue().getEventFailureReason()).contains("publish failed");
        assertThat(savedCaptor.getValue().getEventCorrelationId()).isNotBlank();
    }

    @Test
    void createObjection_whenRepositoryInsertFails_throwsException() {
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        String companyNumber = VALID_COMPANY_NUMBER;
        ObjectionDocument mappedDocument = new ObjectionDocument();
        DataAccessResourceFailureException cause =
                new DataAccessResourceFailureException("mongo insert failed");


        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenThrow(cause);

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto))
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
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        String companyNumber = VALID_COMPANY_NUMBER;

        ArgumentCaptor<String> objectionIdCaptor = ArgumentCaptor.forClass(String.class);

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), eq(PARTNER_ORGANISATION), objectionIdCaptor.capture()))
                .thenReturn(new ObjectionDocument());
        when(objectionRepository.insert(any(ObjectionDocument.class))).thenReturn(new ObjectionDocument());
        when(objectionKafkaProducer.publishObjectionEvent(any(ObjectionDocument.class))).thenReturn(getPublishedEvent("event-id-3"));
        when(objectionResponseMapper.toObjectionApiResponse(any())).thenReturn(new BaseObjectionResponse());

        strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto);
        strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto);

        assertThat(objectionIdCaptor.getAllValues()).hasSize(2);
        assertThat(objectionIdCaptor.getAllValues().get(0)).isNotBlank();
        assertThat(objectionIdCaptor.getAllValues().get(1)).isNotBlank();
        assertThat(objectionIdCaptor.getAllValues().get(0)).isNotEqualTo(objectionIdCaptor.getAllValues().get(1));
    }

    @Test
    void createObjection_whenSaveAfterPublishFails_stillReturnsSuccessResponse() {
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
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

        BaseObjectionResponse result = strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto);

        assertThat(result).isSameAs(expectedResponse);
        verify(objectionRepository).save(any(ObjectionDocument.class));
    }

    @Test
    void createObjection_whenKafkaFailsAndSaveAfterFailureThrows_stillRethrowsOriginalKafkaException() {
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
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

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection(companyNumber, requestDto))
                .isSameAs(kafkaException);

        verify(objectionRepository).save(any(ObjectionDocument.class));
    }

    @Test
    void getObjection_whenObjectionExists_returnsObjection() {
        String companyNumber = VALID_COMPANY_NUMBER;
        String objectionId = "objection-1";
        ObjectionDocument document = new ObjectionDocument();
        BaseObjectionResponse expectedResponse = new BaseObjectionResponse();

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)).thenReturn(document);
        when(objectionResponseMapper.toObjectionApiResponse(document)).thenReturn(expectedResponse);

        BaseObjectionResponse result = strikeOffPartnerObjectionService.getObjection(companyNumber, objectionId);

        assertEquals(expectedResponse, result);
        verify(objectionRepository).findByCompanyNumberAndObjectionId(companyNumber, objectionId);
        verify(objectionResponseMapper).toObjectionApiResponse(document);
    }

    @Test
    void getObjection_whenObjectionDoesNotExist_throwsObjectionNotFoundExceptionWithMessage() {
        when(objectionRepository.findByCompanyNumberAndObjectionId("1", "2")).thenReturn(null);
        ObjectionNotFoundException ex = assertThrows(
                ObjectionNotFoundException.class,
                () -> strikeOffPartnerObjectionService.getObjection("1", "2"));
        assertEquals(
                format("Objection not found for company number=%s, objectionId=%s", "1", "2"),
                ex.getMessage());
        verify(objectionRepository).findByCompanyNumberAndObjectionId("1", "2");
        verifyNoInteractions(objectionResponseMapper);
    }

    @Test
    void createObjection_callsCompanyValidator() {
        CreateObjectionRequest request = validCreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
        BaseObjectionResponse response = new BaseObjectionResponse();

        when(objectionRequestMapper.toObjectionDocument(
                eq(request), eq(VALID_COMPANY_NUMBER), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenReturn(savedDocument);
        when(objectionKafkaProducer.publishObjectionEvent(savedDocument)).thenReturn(getPublishedEvent("event-id-6"));
        when(objectionResponseMapper.toObjectionApiResponse(savedDocument)).thenReturn(response);

        strikeOffPartnerObjectionService.createObjection(VALID_COMPANY_NUMBER, request);

        verify(companyValidator, times(1)).validateCompany(VALID_COMPANY_NUMBER, request.getSubmissionCompanyName());
    }

    @Test
    void createObjection_whenValidatorReturnsInvalid_doesNotProceed() {
        CreateObjectionRequest request = validCreateObjectionRequest();
        CompanyValidationException validationException =
                new CompanyValidationException("Company not found", "COMPANY_NUMBER_NOT_EXIST");

        doThrow(validationException).when(companyValidator).validateCompany(VALID_COMPANY_NUMBER, request.getSubmissionCompanyName());

        assertThatThrownBy(() ->
                strikeOffPartnerObjectionService.createObjection(VALID_COMPANY_NUMBER, request))
                .isSameAs(validationException);

        verify(companyValidator).validateCompany(VALID_COMPANY_NUMBER, request.getSubmissionCompanyName());
        verifyNoInteractions(objectionRequestMapper, objectionRepository, objectionKafkaProducer);
    }

    @Test
    void updateObjectionProcessingStatus_whenSubmitted_updatesToProcessing() {
        String companyNumber = "12345";
        String objectionId = "objection-1";
        UpdateObjectionStatusRequest request = new UpdateObjectionStatusRequest();
        request.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_PROCESSING);
        ObjectionDocument existing = new ObjectionDocument();
        existing.setProcessingStatus("objection-submitted");
        existing.setObjectionId(objectionId);
        existing.setCompanyNumber(companyNumber);

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)).thenReturn(existing);
        when(objectionRequestMapper.getEtag()).thenReturn("etag-2");
        when(objectionRepository.save(any(ObjectionDocument.class))).thenReturn(existing);

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(companyNumber, objectionId, request);

        ArgumentCaptor<ObjectionDocument> captor = ArgumentCaptor.forClass(ObjectionDocument.class);
        verify(objectionRepository).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo("objection-processing");
        assertThat(captor.getValue().getProcessingStatusChangedAt()).isNotNull();
        assertThat(captor.getValue().getEtag()).isEqualTo("etag-2");
    }

    @Test
    void updateObjectionProcessingStatus_whenAlreadyProcessing_returnsWithoutSaving() {
        String companyNumber = "12345";
        String objectionId = "objection-1";
        UpdateObjectionStatusRequest request = new UpdateObjectionStatusRequest();
        request.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_PROCESSING);
        ObjectionDocument existing = new ObjectionDocument();
        existing.setProcessingStatus("objection-processing");

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)).thenReturn(existing);

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(companyNumber, objectionId, request);

        verify(objectionRepository).findByCompanyNumberAndObjectionId(companyNumber, objectionId);
        verifyNoInteractions(objectionRequestMapper);
    }

    @Test
    void updateObjectionProcessingStatus_whenTransitionNotAllowed_throwsConflict() {
        String companyNumber = "12345";
        String objectionId = "objection-1";
        UpdateObjectionStatusRequest request = new UpdateObjectionStatusRequest();
        request.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_PROCESSING);
        ObjectionDocument existing = new ObjectionDocument();
        existing.setProcessingStatus("objection-rejected");

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)).thenReturn(existing);

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                companyNumber,
                objectionId,
                request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(409);
    }

    @Test
    void updateObjectionProcessingStatus_whenRequestedStatusMissing_throwsBadRequest() {
        String companyNumber = "12345";
        String objectionId = "objection-1";
        UpdateObjectionStatusRequest request = new UpdateObjectionStatusRequest();
        ObjectionDocument existing = new ObjectionDocument();
        existing.setProcessingStatus("objection-submitted");

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)).thenReturn(existing);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> strikeOffPartnerObjectionService.updateObjectionProcessingStatus(companyNumber, objectionId, request));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).isEqualTo("Unsupported status=null");
    }

    @Test
    void parseRequestedStatus_whenStatusIsEmpty_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(strikeOffPartnerObjectionService, "parseRequestedStatus", ""));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).isEqualTo("Unsupported status=");
    }

    @Test
    void parseRequestedStatus_whenStatusIsUnsupported_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        strikeOffPartnerObjectionService,
                        "parseRequestedStatus",
                        "unsupported-status"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).isEqualTo("Unsupported status=unsupported-status");
    }

    @Test
    void updateObjectionProcessingStatus_whenProcessing_updatesToAccepted() {
        String companyNumber = "12345";
        String objectionId = "objection-1";
        UpdateObjectionStatusRequest request = new UpdateObjectionStatusRequest();
        request.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_ACCEPTED);
        ObjectionDocument existing = new ObjectionDocument();
        existing.setProcessingStatus("objection-processing");

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)).thenReturn(existing);
        when(objectionRequestMapper.getEtag()).thenReturn("etag-3");
        when(objectionRepository.save(any(ObjectionDocument.class))).thenReturn(existing);

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(companyNumber, objectionId, request);

        ArgumentCaptor<ObjectionDocument> captor = ArgumentCaptor.forClass(ObjectionDocument.class);
        verify(objectionRepository).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo("objection-accepted");
    }

    @Test
    void updateObjectionProcessingStatus_whenProcessing_updatesToRejected() {
        String companyNumber = "12345";
        String objectionId = "objection-1";
        UpdateObjectionStatusRequest request = new UpdateObjectionStatusRequest();
        request.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_REJECTED);
        ObjectionDocument existing = new ObjectionDocument();
        existing.setProcessingStatus("objection-processing");

        when(objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)).thenReturn(existing);
        when(objectionRequestMapper.getEtag()).thenReturn("etag-4");
        when(objectionRepository.save(any(ObjectionDocument.class))).thenReturn(existing);

        strikeOffPartnerObjectionService.updateObjectionProcessingStatus(companyNumber, objectionId, request);

        ArgumentCaptor<ObjectionDocument> captor = ArgumentCaptor.forClass(ObjectionDocument.class);
        verify(objectionRepository).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo("objection-rejected");
    }
    
    private StrikeOffPartnerObjections getPublishedEvent(String eventId) {
        return StrikeOffPartnerObjections.newBuilder()
                .setEventId(eventId)
                .setEventType(EventType.OBJECTION)
                .setEventTime(java.time.Instant.now().toString())
                .setSource("strike-off-partner-objections-api")
                .setPartnerOrganisation("hmrc")
                .setStrikeOffEventId(UUID.randomUUID().toString())
                .build();
    }

    private CreateObjectionRequest validCreateObjectionRequest() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setSubmissionCompanyName("Test Company Ltd");
        return request;
    }
}
