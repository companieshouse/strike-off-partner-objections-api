package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;

public class WithdrawalStatusRepositoryImpl implements WithdrawalStatusRepository {

    private final MongoTemplate mongoTemplate;

    public WithdrawalStatusRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public UpdateResult updateEventStatus(String companyNumber, String withdrawalId, EventStatus status,
                                         Instant changedAt, String correlationId, String failureReason) {
        Update update = new Update().set("eventStatus", status)
                .set("eventStatusChangedAt", changedAt).set("eventCorrelationId", correlationId)
                .set("eventFailureReason", failureReason);
        return mongoTemplate.updateFirst(query(companyNumber, withdrawalId), update, WithdrawalDocument.class);
    }

    @Override
    public Optional<WithdrawalDocument> updateProcessingStatus(
            String companyNumber, String withdrawalId, String expectedStatus, String requestedStatus,
            Instant changedAt, String etag) {
        Query query = query(companyNumber, withdrawalId)
                .addCriteria(Criteria.where("processingStatus").is(expectedStatus));
        Update update = new Update().set("processingStatus", requestedStatus)
                .set("processingStatusChangedAt", changedAt).set("etag", etag);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), WithdrawalDocument.class));
    }

    @Override
    public UpdateResult updateCallbackStatus(String companyNumber, String withdrawalId, CallbackStatus status,
                                            Instant changedAt, String correlationId, String failureReason) {
        Update update = new Update().set("callbackStatus", status)
                .set("callbackStatusChangedAt", changedAt).set("callbackCorrelationId", correlationId)
                .set("callbackFailureReason", failureReason);
        return mongoTemplate.updateFirst(query(companyNumber, withdrawalId), update, WithdrawalDocument.class);
    }

    private Query query(String companyNumber, String withdrawalId) {
        return Query.query(Criteria.where("companyNumber").is(companyNumber).and("withdrawalId").is(withdrawalId));
    }
}
