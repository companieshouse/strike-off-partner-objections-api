package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.UpdateObjectionStatusRequest;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.MongoDbIntegration;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.ObjectionKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionRequestMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionResponseMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

@Tag("integration-test")
@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig(StrikeOffPartnerObjectionEventStatusIntegrationTest.MongoTestConfiguration.class)
class StrikeOffPartnerObjectionEventStatusIntegrationTest extends MongoDbIntegration {

    private static final String COMPANY_NUMBER = "01234567";
    private static final String OBJECTION_ID = "objection-event-status";
    private static final String PARTNER_ORGANISATION = "HMRC";
    private static final String EVENT_ID = "event-id";
    private static final String FAILURE_REASON = "publish failed";
    private static final String UPDATED_ETAG = "processor-etag";
    private static final String CALLBACK_CORRELATION_ID = "callback-id";
    private static final String CALLBACK_FAILURE_REASON = "callback failed";
    private static final Instant SUBMITTED_AT = Instant.parse("2026-01-01T11:59:00Z");
    private static final Instant PROCESSING_CHANGED_AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant CALLBACK_CHANGED_AT = Instant.parse("2026-01-01T12:01:00Z");

    @Autowired
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
    private EmailValidator emailValidator;
    @Mock
    private HmrcCallbackService hmrcCallbackService;

    private StrikeOffPartnerObjectionService service;

    @BeforeEach
    void setUp() {
        objectionRepository.deleteAll();
        service = new StrikeOffPartnerObjectionService(
                objectionRepository, objectionRequestMapper, objectionResponseMapper,
                objectionKafkaProducer, companyValidator, emailValidator, hmrcCallbackService);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "FAILED"})
    void createObjection_whenProcessingUpdatesDuringPublish_persistsOnlyEventFields(EventStatus eventStatus) {
        CreateObjectionRequest request = new CreateObjectionRequest();
        ObjectionDocument staleDocument = createSubmittedObjection();
        when(objectionRequestMapper.toObjectionDocument(
                eq(request), eq(COMPANY_NUMBER), eq(PARTNER_ORGANISATION), anyString()))
                .thenReturn(staleDocument);
        KafkaPublishException publishException = new KafkaPublishException(
                FAILURE_REASON, EVENT_ID, new IllegalStateException("Kafka unavailable"));
        when(objectionKafkaProducer.publishObjectionEvent(any())).thenAnswer(invocation -> {
            persistNewerProcessingState();
            if (eventStatus == EventStatus.FAILED) {
                throw publishException;
            }
            StrikeOffPartnerObjections event = new StrikeOffPartnerObjections();
            event.setEventId(EVENT_ID);
            return event;
        });
        Instant beforePublish = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        if (eventStatus == EventStatus.FAILED) {
            assertThatThrownBy(() -> service.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                    .isSameAs(publishException);
        } else {
            BaseObjectionResponse response = new BaseObjectionResponse();
            when(objectionResponseMapper.toObjectionApiResponse(staleDocument)).thenReturn(response);
            assertThat(service.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION)).isSameAs(response);
        }

        ObjectionDocument persisted = findObjection();
        assertThat(staleDocument.getProcessingStatus())
                .isEqualTo(ObjectionProcessingStatus.OBJECTION_SUBMITTED.getValue());
        assertThat(persisted.getEventStatus()).isEqualTo(eventStatus);
        assertThat(persisted.getEventCorrelationId()).isEqualTo(EVENT_ID);
        assertThat(persisted.getEventStatusChangedAt()).isBetween(beforePublish, Instant.now());
        assertThat(persisted.getEventStatusChangedAt())
                .isEqualTo(staleDocument.getEventStatusChangedAt().truncatedTo(ChronoUnit.MILLIS));
        assertThat(persisted.getEventFailureReason())
                .isEqualTo(eventStatus == EventStatus.FAILED ? FAILURE_REASON : null);
        assertNewerProcessingState(persisted);
    }

    private ObjectionDocument createSubmittedObjection() {
        ObjectionDocument document = new ObjectionDocument();
        document.setCompanyNumber(COMPANY_NUMBER);
        document.setObjectionId(OBJECTION_ID);
        document.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_SUBMITTED.getValue());
        document.setProcessingStatusChangedAt(SUBMITTED_AT);
        document.setEtag("original-etag");
        document.setEventFailureReason("previous publish failure");
        return document;
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "FAILED"})
    void updateProcessingStatus_whenEventUpdatesAfterRead_preservesEventAndCallbackFields(EventStatus status) {
        ObjectionDocument document = createSubmittedObjection();
        document.setEventStatus(EventStatus.PENDING);
        document.setCallbackStatus(CallbackStatus.FAILED);
        document.setCallbackStatusChangedAt(CALLBACK_CHANGED_AT);
        document.setCallbackCorrelationId(CALLBACK_CORRELATION_ID);
        document.setCallbackFailureReason(CALLBACK_FAILURE_REASON);
        objectionRepository.insert(document);
        when(objectionRequestMapper.getEtag()).thenAnswer(invocation -> {
            objectionRepository.updateEventStatus(COMPANY_NUMBER, OBJECTION_ID, status, PROCESSING_CHANGED_AT,
                    EVENT_ID, status == EventStatus.FAILED ? FAILURE_REASON : null);
            return UPDATED_ETAG;
        });
        UpdateObjectionStatusRequest request = processingRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING);

        service.updateObjectionProcessingStatus(COMPANY_NUMBER, OBJECTION_ID, request);

        ObjectionDocument persisted = findObjection();
        assertThat(persisted.getProcessingStatus())
                .isEqualTo(ObjectionProcessingStatus.OBJECTION_PROCESSING.getValue());
        assertThat(persisted.getProcessingStatusChangedAt()).isAfter(SUBMITTED_AT);
        assertThat(persisted.getEtag()).isEqualTo(UPDATED_ETAG);
        assertThat(persisted.getEventStatus()).isEqualTo(status);
        assertThat(persisted.getEventStatusChangedAt()).isEqualTo(PROCESSING_CHANGED_AT);
        assertThat(persisted.getEventCorrelationId()).isEqualTo(EVENT_ID);
        assertThat(persisted.getEventFailureReason()).isEqualTo(status == EventStatus.FAILED ? FAILURE_REASON : null);
        assertThat(persisted.getCallbackStatus()).isEqualTo(CallbackStatus.FAILED);
        assertThat(persisted.getCallbackStatusChangedAt()).isEqualTo(CALLBACK_CHANGED_AT);
        assertThat(persisted.getCallbackCorrelationId()).isEqualTo(CALLBACK_CORRELATION_ID);
        assertThat(persisted.getCallbackFailureReason()).isEqualTo(CALLBACK_FAILURE_REASON);
        verifyNoInteractions(hmrcCallbackService);
    }

    @Test
    void updateProcessingStatus_whenConcurrentTransitionWins_preservesWinningStatusAndDoesNotSendCallback() {
        ObjectionDocument document = createSubmittedObjection();
        document.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_PROCESSING.getValue());
        objectionRepository.insert(document);
        when(objectionRequestMapper.getEtag()).thenAnswer(invocation -> {
            objectionRepository.updateProcessingStatus(COMPANY_NUMBER, OBJECTION_ID,
                    ObjectionProcessingStatus.OBJECTION_PROCESSING.getValue(),
                    ObjectionProcessingStatus.OBJECTION_REJECTED.getValue(), PROCESSING_CHANGED_AT, UPDATED_ETAG);
            return "losing-etag";
        });
        UpdateObjectionStatusRequest request = processingRequest(ObjectionProcessingStatus.OBJECTION_ACCEPTED);

        assertThatThrownBy(() -> service.updateObjectionProcessingStatus(COMPANY_NUMBER, OBJECTION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT.value());

        ObjectionDocument persisted = findObjection();
        assertThat(persisted.getProcessingStatus()).isEqualTo(ObjectionProcessingStatus.OBJECTION_REJECTED.getValue());
        assertThat(persisted.getProcessingStatusChangedAt()).isEqualTo(PROCESSING_CHANGED_AT);
        assertThat(persisted.getEtag()).isEqualTo(UPDATED_ETAG);
        verifyNoInteractions(hmrcCallbackService);
    }

    @Test
    void updateProcessingStatus_whenDocumentIsRemoved_returnsConflictWithoutRecreatingDocument() {
        objectionRepository.insert(createSubmittedObjection());
        when(objectionRequestMapper.getEtag()).thenAnswer(invocation -> {
            objectionRepository.deleteAll();
            return UPDATED_ETAG;
        });
        UpdateObjectionStatusRequest request = processingRequest(ObjectionProcessingStatus.OBJECTION_PROCESSING);

        assertThatThrownBy(() -> service.updateObjectionProcessingStatus(COMPANY_NUMBER, OBJECTION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT.value());

        assertThat(objectionRepository.findAll()).isEmpty();
        verifyNoInteractions(hmrcCallbackService);
    }

    private UpdateObjectionStatusRequest processingRequest(ObjectionProcessingStatus status) {
        UpdateObjectionStatusRequest request = new UpdateObjectionStatusRequest();
        request.setProcessingStatus(status);
        return request;
    }

    private void persistNewerProcessingState() {
        ObjectionDocument newerDocument = findObjection();
        newerDocument.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_PROCESSING.getValue());
        newerDocument.setProcessingStatusChangedAt(PROCESSING_CHANGED_AT);
        newerDocument.setEtag(UPDATED_ETAG);
        newerDocument.setCallbackStatus(CallbackStatus.FAILED);
        newerDocument.setCallbackStatusChangedAt(CALLBACK_CHANGED_AT);
        newerDocument.setCallbackCorrelationId(CALLBACK_CORRELATION_ID);
        newerDocument.setCallbackFailureReason(CALLBACK_FAILURE_REASON);
        objectionRepository.save(newerDocument);
    }

    private ObjectionDocument findObjection() {
        return objectionRepository.findByCompanyNumberAndObjectionId(COMPANY_NUMBER, OBJECTION_ID)
                .orElseThrow(() -> new AssertionError("Objection was not persisted"));
    }

    private void assertNewerProcessingState(ObjectionDocument persisted) {
        assertThat(persisted.getProcessingStatus())
                .isEqualTo(ObjectionProcessingStatus.OBJECTION_PROCESSING.getValue());
        assertThat(persisted.getProcessingStatusChangedAt()).isEqualTo(PROCESSING_CHANGED_AT);
        assertThat(persisted.getEtag()).isEqualTo(UPDATED_ETAG);
        assertThat(persisted.getCallbackStatus()).isEqualTo(CallbackStatus.FAILED);
        assertThat(persisted.getCallbackStatusChangedAt()).isEqualTo(CALLBACK_CHANGED_AT);
        assertThat(persisted.getCallbackCorrelationId()).isEqualTo(CALLBACK_CORRELATION_ID);
        assertThat(persisted.getCallbackFailureReason()).isEqualTo(CALLBACK_FAILURE_REASON);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMongoRepositories(basePackageClasses = ObjectionRepository.class)
    static class MongoTestConfiguration {

        @Bean
        MongoClient mongoClient(@Value("${spring.mongodb.uri}") String uri) {
            return MongoClients.create(uri);
        }

        @Bean
        MongoTemplate mongoTemplate(MongoClient mongoClient) {
            return new MongoTemplate(mongoClient, "objection_event_status_test");
        }
    }
}
