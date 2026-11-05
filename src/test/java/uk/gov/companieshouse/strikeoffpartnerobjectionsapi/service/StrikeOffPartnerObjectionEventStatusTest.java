package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.client.result.UpdateResult;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.logging.StructuredLogger;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.ObjectionKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionRequestMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionResponseMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerObjectionEventStatusTest {

    private static final String COMPANY_NUMBER = "01234567";
    private static final String OBJECTION_ID = "objection-1";
    private static final String PARTNER_ORGANISATION = "hmrc";
    private static final String EVENT_ID = "event-1";
    private static final String FAILURE_REASON = "publish failed";

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
    private EmailValidator emailValidator;
    @Mock
    private HmrcCallbackService hmrcCallbackService;
    @InjectMocks
    private StrikeOffPartnerObjectionService service;

    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(StructuredLogger.class);
    private final CreateObjectionRequest request = new CreateObjectionRequest();
    private final BaseObjectionResponse response = new BaseObjectionResponse();
    private ObjectionDocument document;

    @BeforeEach
    void setUp() {
        logAppender.start();
        logger.addAppender(logAppender);
        document = new ObjectionDocument();
        document.setCompanyNumber(COMPANY_NUMBER);
        document.setObjectionId(OBJECTION_ID);
        when(objectionRequestMapper.toObjectionDocument(
                eq(request), eq(COMPANY_NUMBER), eq(PARTNER_ORGANISATION), anyString())).thenReturn(document);
        when(objectionRepository.insert(document)).thenReturn(document);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @ParameterizedTest
    @MethodSource("writeOutcomes")
    void createObjection_logsWriteOutcomeWithoutChangingPublishBehaviour(
            EventStatus status, UpdateResult result, String message, Level level) {
        KafkaPublishException publishFailure = stubPublish(status);
        when(objectionRepository.updateEventStatus(
                eq(COMPANY_NUMBER), eq(OBJECTION_ID), eq(status), any(), eq(EVENT_ID), any())).thenReturn(result);

        assertPublishBehaviour(status, publishFailure);

        Map<Object, Object> data = assertLog(message, level, status);
        if (result == null) {
            assertThat(data).doesNotContainKey("acknowledged");
        } else {
            assertThat(data).containsEntry("acknowledged", result.wasAcknowledged());
        }
        if (result != null && result.wasAcknowledged()) {
            assertThat(data).containsEntry("matched_count", result.getMatchedCount())
                    .containsEntry("modified_count", result.getModifiedCount());
        } else {
            assertThat(data).doesNotContainKeys("matched_count", "modified_count");
        }
        assertThat(logAppender.list.stream().filter(event -> event.getMessage().equals(message))).hasSize(1);
        if (level == Level.ERROR) {
            assertThat(logAppender.list).noneMatch(event ->
                    event.getMessage().equals("Objection event status updated")
                            || event.getMessage().equals("Objection event status unchanged"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "FAILED"})
    void createObjection_whenEventPersistenceFails_logsExceptionAndPreservesPublishBehaviour(EventStatus status) {
        KafkaPublishException publishFailure = stubPublish(status);
        DataAccessResourceFailureException persistenceFailure =
                new DataAccessResourceFailureException("Mongo update failed");
        when(objectionRepository.updateEventStatus(
                eq(COMPANY_NUMBER), eq(OBJECTION_ID), eq(status), any(), eq(EVENT_ID), any()))
                .thenThrow(persistenceFailure);

        assertPublishBehaviour(status, publishFailure);

        Map<Object, Object> data = assertLog("Failed to save objection event status", Level.ERROR, status);
        assertThat(data).containsEntry("error_message", "Failed to update event status after publish");
        ILoggingEvent error = findLog("Failed to save objection event status");
        assertThat(error.getArgumentArray()[0].toString())
                .contains(DataAccessResourceFailureException.class.getName(), persistenceFailure.getMessage());
    }

    private static Stream<Arguments> writeOutcomes() {
        return Stream.of(EventStatus.PUBLISHED, EventStatus.FAILED).flatMap(status -> Stream.of(
                Arguments.of(status, null, "Objection event status update outcome unavailable", Level.ERROR),
                Arguments.of(status, UpdateResult.unacknowledged(),
                        "Objection event status update was not acknowledged", Level.ERROR),
                Arguments.of(status, UpdateResult.acknowledged(0, 0L, null),
                        "Objection event status update matched no document", Level.ERROR),
                Arguments.of(status, UpdateResult.acknowledged(1, 0L, null),
                        "Objection event status unchanged", Level.INFO),
                Arguments.of(status, UpdateResult.acknowledged(1, 1L, null),
                        "Objection event status updated", Level.INFO)));
    }

    private KafkaPublishException stubPublish(EventStatus status) {
        KafkaPublishException failure = new KafkaPublishException(
                FAILURE_REASON, EVENT_ID, new IllegalStateException("Kafka unavailable"));
        if (status == EventStatus.FAILED) {
            when(objectionKafkaProducer.publishObjectionEvent(document)).thenThrow(failure);
        } else {
            StrikeOffPartnerObjections event = new StrikeOffPartnerObjections();
            event.setEventId(EVENT_ID);
            when(objectionKafkaProducer.publishObjectionEvent(document)).thenReturn(event);
            when(objectionResponseMapper.toObjectionApiResponse(document)).thenReturn(response);
        }
        return failure;
    }

    private void assertPublishBehaviour(EventStatus status, KafkaPublishException publishFailure) {
        if (status == EventStatus.FAILED) {
            assertThatThrownBy(() -> service.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                    .isSameAs(publishFailure);
        } else {
            assertThat(service.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION)).isSameAs(response);
        }
        verify(objectionRepository).updateEventStatus(
                COMPANY_NUMBER, OBJECTION_ID, status, document.getEventStatusChangedAt(),
                EVENT_ID, status == EventStatus.FAILED ? FAILURE_REASON : null);
        verify(objectionRepository, never()).save(any(ObjectionDocument.class));
    }

    private Map<Object, Object> assertLog(String message, Level level, EventStatus status) {
        ILoggingEvent event = findLog(message);
        assertThat(event.getLevel()).isEqualTo(level);
        assertThat(event.getArgumentArray()[0]).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) event.getArgumentArray()[0];
        assertThat(payload.get("data")).isInstanceOf(Map.class);
        Map<Object, Object> data = new HashMap<>((Map<?, ?>) payload.get("data"));
        assertThat(data).containsEntry("company_number", COMPANY_NUMBER)
                .containsEntry("resource_id", OBJECTION_ID)
                .containsEntry("correlation_id", EVENT_ID)
                .containsEntry("status", status.name());
        return data;
    }

    private ILoggingEvent findLog(String message) {
        return logAppender.list.stream().filter(event -> event.getMessage().equals(message))
                .findFirst().orElseThrow(() -> new AssertionError("Missing log: " + message));
    }
}
