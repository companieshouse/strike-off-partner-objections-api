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
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;
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
    private CompanyProfileService companyProfileService;
    
    @Mock
    private ObjectionKafkaProducer objectionKafkaProducer;
    private StrikeOffPartnerObjectionService strikeOffPartnerObjectionService;

    @BeforeEach
    void setUp() {
        strikeOffPartnerObjectionService = new StrikeOffPartnerObjectionService(
                objectionRepository,
                objectionRequestMapper,
                objectionResponseMapper,
                companyProfileService,
                objectionKafkaProducer

        );
    }
    @Test
    void createObjection_whenRequestIsValid_returnsMappedResponse() {
        stubCompanyProfile(validCompanyProfile());
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
        String companyNumber = "12345";

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
        stubCompanyProfile(validCompanyProfile());
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
        String companyNumber = "12345";
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
        stubCompanyProfile(validCompanyProfile());
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        String companyNumber = "12345";
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
        stubCompanyProfile(validCompanyProfile());
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        String companyNumber = "12345";

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
        stubCompanyProfile(validCompanyProfile());
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
        String companyNumber = "12345";
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
        stubCompanyProfile(validCompanyProfile());
        CreateObjectionRequest requestDto = validCreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
        String companyNumber = "12345";
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
        String companyNumber = "12345";
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
    void createObjection_whenCompanyProfileApiReturns404_throwsCompanyNumberNotExistValidationError() {
        CreateObjectionRequest request = validCreateObjectionRequest();
        ServiceException notFoundException = new ServiceException(
                "Error retrieving company profile",
                apiErrorResponseException(404, "Not Found"));

        when(companyProfileService.getCompanyProfile("12345")).thenThrow(notFoundException);

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection("12345", request))
                .isInstanceOf(CompanyValidationException.class)
                .extracting(ex -> ((CompanyValidationException) ex).getErrorCode())
                .isEqualTo(StrikeOffPartnerObjectionService.COMPANY_NUMBER_NOT_EXIST);

        verifyNoInteractions(objectionRequestMapper, objectionRepository, objectionKafkaProducer, objectionResponseMapper);
    }

    @Test
    void createObjection_whenSubmissionCompanyNameDoesNotMatch_throwsCompanyNameMismatchValidationError() {
        CompanyProfileApi companyProfile = validCompanyProfile();
        companyProfile.setCompanyName("Different Co Ltd");
        stubCompanyProfile(companyProfile);
        CreateObjectionRequest request = validCreateObjectionRequest();

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection("12345", request))
                .isInstanceOf(CompanyValidationException.class)
                .extracting(ex -> ((CompanyValidationException) ex).getErrorCode())
                .isEqualTo(StrikeOffPartnerObjectionService.SUBMISSION_COMPANY_NAME_MISMATCH);

        verifyNoInteractions(objectionRequestMapper, objectionRepository, objectionKafkaProducer, objectionResponseMapper);
    }

    @Test
    void createObjection_whenCompanyTypeIsNotEligible_throwsInvalidCompanyTypeValidationError() {
        CompanyProfileApi companyProfile = validCompanyProfile();
        companyProfile.setType("charitable-incorporated-organisation");
        stubCompanyProfile(companyProfile);
        CreateObjectionRequest request = validCreateObjectionRequest();

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection("12345", request))
                .isInstanceOf(CompanyValidationException.class)
                .extracting(ex -> ((CompanyValidationException) ex).getErrorCode())
                .isEqualTo(StrikeOffPartnerObjectionService.INVALID_COMPANY_TYPE);

        verifyNoInteractions(objectionRequestMapper, objectionRepository, objectionKafkaProducer, objectionResponseMapper);
    }

    @Test
    void createObjection_whenCompanyHasNoActiveStrikeOffProposal_throwsInvalidCompanyStatusValidationError() {
        CompanyProfileApi companyProfile = validCompanyProfile();
        companyProfile.setCompanyStatusDetail("active");
        stubCompanyProfile(companyProfile);
        CreateObjectionRequest request = validCreateObjectionRequest();

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection("12345", request))
                .isInstanceOf(CompanyValidationException.class)
                .extracting(ex -> ((CompanyValidationException) ex).getErrorCode())
                .isEqualTo(StrikeOffPartnerObjectionService.INVALID_COMPANY_STATUS);

        verifyNoInteractions(objectionRequestMapper, objectionRepository, objectionKafkaProducer, objectionResponseMapper);
    }

    @Test
    void createObjection_whenCompanyProfileApiIsUnavailable_propagatesServiceException() {
        CreateObjectionRequest request = validCreateObjectionRequest();
        ServiceException downstreamException = new ServiceException(
                "Error retrieving company profile",
                apiErrorResponseException(503, "Service Unavailable"));

        when(companyProfileService.getCompanyProfile("12345")).thenThrow(downstreamException);

        assertThatThrownBy(() -> strikeOffPartnerObjectionService.createObjection("12345", request))
                .isSameAs(downstreamException);

        verifyNoInteractions(objectionRequestMapper, objectionRepository, objectionKafkaProducer, objectionResponseMapper);
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

    private void stubCompanyProfile(CompanyProfileApi companyProfileApi) {
        when(companyProfileService.getCompanyProfile(anyString())).thenReturn(companyProfileApi);
    }

    private CreateObjectionRequest validCreateObjectionRequest() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setSubmissionCompanyName("Test Company Ltd");
        return request;
    }

    private CompanyProfileApi validCompanyProfile() {
        CompanyProfileApi company = new CompanyProfileApi();
        company.setCompanyName("Test Company Ltd");
        company.setType("ltd");
        company.setCompanyStatusDetail("active-proposal-to-strike-off");
        return company;
    }

    private ApiErrorResponseException apiErrorResponseException(int statusCode, String statusMessage) {
        HttpResponseException.Builder builder = new HttpResponseException.Builder(
                statusCode,
                statusMessage,
                new HttpHeaders());
        return new ApiErrorResponseException(builder);
    }
}
