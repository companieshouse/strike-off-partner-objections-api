package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class ObjectionProcessingStatusRepositoryImplTest {

    private static final String COMPANY_NUMBER = "01234567";
    private static final String OBJECTION_ID = "objection-1";
    private static final String EXPECTED_STATUS = "objection-submitted";
    private static final String REQUESTED_STATUS = "objection-processing";
    private static final String ETAG = "new-etag";
    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private MongoTemplate mongoTemplate;
    @InjectMocks
    private ObjectionProcessingStatusRepositoryImpl repository;

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
