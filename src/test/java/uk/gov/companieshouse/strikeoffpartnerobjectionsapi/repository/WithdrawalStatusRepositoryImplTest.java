package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class WithdrawalStatusRepositoryImplTest {

    private static final String COMPANY = "01234567";
    private static final String ID = "withdrawal-1";
    private static final String CORRELATION = "correlation-1";
    private static final Instant CHANGED_AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final String EXPECTED = "withdrawal-requested";
    private static final String REQUESTED = "withdrawal-processing";
    private static final String ETAG = "new-etag";

    @Mock
    private MongoTemplate mongoTemplate;
    @InjectMocks
    private WithdrawalStatusRepositoryImpl repository;

    @ParameterizedTest
    @MethodSource("trackingUpdates")
    void trackingUpdate_setsOnlyOwnedFieldsAndReturnsOutcome(String prefix, Enum<?> status, String failure) {
        UpdateResult outcome = UpdateResult.acknowledged(1, 1L, null);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(WithdrawalDocument.class)))
                .thenReturn(outcome);

        UpdateResult actual = updateTracking(prefix, status, failure);

        assertThat(actual).isSameAs(outcome);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(WithdrawalDocument.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(identifierQuery());
        assertThat(update.getValue().getUpdateObject()).isEqualTo(new Document("$set",
                new Document(prefix + "Status", status).append(prefix + "StatusChangedAt", CHANGED_AT)
                        .append(prefix + "CorrelationId", CORRELATION).append(prefix + "FailureReason", failure)));
    }

    @ParameterizedTest
    @MethodSource("trackingUpdates")
    void trackingUpdate_propagatesPersistenceFailure(String prefix, Enum<?> status, String failureReason) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Mongo update failed");
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(WithdrawalDocument.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> updateTracking(prefix, status, failureReason)).isSameAs(failure);
    }

    @Test
    void processingUpdate_checksExpectedStatusAndReturnsUpdatedDocumentWithoutUpsert() {
        WithdrawalDocument document = new WithdrawalDocument();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(WithdrawalDocument.class))).thenReturn(document);

        assertThat(repository.updateProcessingStatus(COMPANY, ID, EXPECTED, REQUESTED, CHANGED_AT, ETAG))
                .containsSame(document);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        ArgumentCaptor<FindAndModifyOptions> options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), options.capture(),
                eq(WithdrawalDocument.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(identifierQuery().append("processingStatus", EXPECTED));
        assertThat(update.getValue().getUpdateObject()).isEqualTo(new Document("$set",
                new Document("processingStatus", REQUESTED).append("processingStatusChangedAt", CHANGED_AT)
                        .append("etag", ETAG)));
        assertThat(options.getValue().isReturnNew()).isTrue();
        assertThat(options.getValue().isUpsert()).isFalse();
    }

    @Test
    void processingUpdate_returnsEmptyWhenNoDocumentMatches() {
        assertThat(repository.updateProcessingStatus(COMPANY, ID, EXPECTED, REQUESTED, CHANGED_AT, ETAG)).isEmpty();
    }

    @Test
    void processingUpdate_propagatesPersistenceFailure() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Mongo update failed");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(WithdrawalDocument.class))).thenThrow(failure);

        assertThatThrownBy(() -> repository.updateProcessingStatus(
                COMPANY, ID, EXPECTED, REQUESTED, CHANGED_AT, ETAG)).isSameAs(failure);
    }

    private static Stream<Arguments> trackingUpdates() {
        return Stream.of(
                Arguments.of("event", EventStatus.PUBLISHED, null),
                Arguments.of("event", EventStatus.FAILED, "publish failed"),
                Arguments.of("callback", CallbackStatus.SUCCESS, null),
                Arguments.of("callback", CallbackStatus.FAILED, "callback failed"));
    }

    private UpdateResult updateTracking(String prefix, Enum<?> status, String failure) {
        if ("event".equals(prefix)) {
            return repository.updateEventStatus(COMPANY, ID, (EventStatus) status, CHANGED_AT, CORRELATION, failure);
        }
        return repository.updateCallbackStatus(COMPANY, ID, (CallbackStatus) status, CHANGED_AT, CORRELATION, failure);
    }

    private Document identifierQuery() {
        return new Document("companyNumber", COMPANY).append("withdrawalId", ID);
    }
}
