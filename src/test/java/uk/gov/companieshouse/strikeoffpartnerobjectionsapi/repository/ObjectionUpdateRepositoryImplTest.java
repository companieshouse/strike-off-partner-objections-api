package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class ObjectionUpdateRepositoryImplTest {

    private static final String COMPANY_NUMBER = "01234567";
    private static final String OBJECTION_ID = "objection-1";
    private static final String EVENT_ID = "event-1";
    private static final String FAILURE_REASON = "publish failed";
    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final String EXPECTED_STATUS = "objection-submitted";
    private static final String REQUESTED_STATUS = "objection-processing";
    private static final String ETAG = "new-etag";

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ObjectionUpdateRepositoryImpl repository;

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "FAILED"})
    void updateEventStatus_updatesOnlyEventFieldsAndReturnsWriteOutcome(EventStatus status) {
        UpdateResult expectedResult = UpdateResult.acknowledged(1, 1L, null);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ObjectionDocument.class)))
                .thenReturn(expectedResult);
        String failureReason = status == EventStatus.FAILED ? FAILURE_REASON : null;

        UpdateResult result = repository.updateEventStatus(
                COMPANY_NUMBER, OBJECTION_ID, status, STATUS_CHANGED_AT, EVENT_ID, failureReason);

        assertThat(result).isSameAs(expectedResult);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(ObjectionDocument.class));
        assertThat(queryCaptor.getValue().getQueryObject())
                .isEqualTo(new Document("companyNumber", COMPANY_NUMBER).append("objectionId", OBJECTION_ID));
        Map<String, Object> expectedFields = new LinkedHashMap<>();
        expectedFields.put("eventStatus", status);
        expectedFields.put("eventStatusChangedAt", STATUS_CHANGED_AT);
        expectedFields.put("eventCorrelationId", EVENT_ID);
        expectedFields.put("eventFailureReason", failureReason);
        assertThat(updateCaptor.getValue().getUpdateObject())
                .isEqualTo(new Document("$set", new Document(expectedFields)));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void updateEventStatus_whenPersistenceFails_propagatesOriginalException() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Mongo update failed");
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ObjectionDocument.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> repository.updateEventStatus(
                COMPANY_NUMBER, OBJECTION_ID, EventStatus.FAILED, STATUS_CHANGED_AT, EVENT_ID, FAILURE_REASON))
                .isSameAs(failure);
    }

    @Test
    void updateProcessingStatus_guardsExpectedStatusAndUpdatesOnlyProcessingFields() {
        ObjectionDocument updated = new ObjectionDocument();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ObjectionDocument.class))).thenReturn(updated);

        assertThat(repository.updateProcessingStatus(
                COMPANY_NUMBER, OBJECTION_ID, EXPECTED_STATUS, REQUESTED_STATUS, STATUS_CHANGED_AT, ETAG))
                .containsSame(updated);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        ArgumentCaptor<FindAndModifyOptions> options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), options.capture(),
                eq(ObjectionDocument.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("companyNumber", COMPANY_NUMBER)
                .append("objectionId", OBJECTION_ID).append("processingStatus", EXPECTED_STATUS));
        assertThat(update.getValue().getUpdateObject()).isEqualTo(new Document("$set",
                new Document("processingStatus", REQUESTED_STATUS)
                        .append("processingStatusChangedAt", STATUS_CHANGED_AT).append("etag", ETAG)));
        assertThat(options.getValue().isReturnNew()).isTrue();
        assertThat(options.getValue().isUpsert()).isFalse();
    }

    @Test
    void updateProcessingStatus_whenNoDocumentMatches_returnsEmpty() {
        assertThat(repository.updateProcessingStatus(
                COMPANY_NUMBER, OBJECTION_ID, EXPECTED_STATUS, REQUESTED_STATUS, STATUS_CHANGED_AT, ETAG)).isEmpty();
    }

    @Test
    void updateProcessingStatus_whenPersistenceFails_propagatesException() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Mongo update failed");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ObjectionDocument.class))).thenThrow(failure);

        assertThatThrownBy(() -> repository.updateProcessingStatus(
                COMPANY_NUMBER, OBJECTION_ID, EXPECTED_STATUS, REQUESTED_STATUS, STATUS_CHANGED_AT, ETAG))
                .isSameAs(failure);
    }
}
