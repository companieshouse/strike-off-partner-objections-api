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
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

public class ObjectionUpdateRepositoryImpl implements ObjectionUpdateRepository {

    private final MongoTemplate mongoTemplate;

    public ObjectionUpdateRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public UpdateResult updateEventStatus(String companyNumber, String objectionId, EventStatus eventStatus,
                                         Instant eventStatusChangedAt, String eventCorrelationId,
                                         String eventFailureReason) {
        Query query = query(companyNumber, objectionId);
        Update update = new Update()
                .set("eventStatus", eventStatus)
                .set("eventStatusChangedAt", eventStatusChangedAt)
                .set("eventCorrelationId", eventCorrelationId)
                .set("eventFailureReason", eventFailureReason);
        return mongoTemplate.updateFirst(query, update, ObjectionDocument.class);
    }

    @Override
    public Optional<ObjectionDocument> updateProcessingStatus(
            String companyNumber, String objectionId, String expectedStatus, String requestedStatus,
            Instant statusChangedAt, String etag) {
        Query query = query(companyNumber, objectionId)
                .addCriteria(Criteria.where("processingStatus").is(expectedStatus));
        Update update = new Update()
                .set("processingStatus", requestedStatus)
                .set("processingStatusChangedAt", statusChangedAt)
                .set("etag", etag);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ObjectionDocument.class));
    }

    private Query query(String companyNumber, String objectionId) {
        return Query.query(Criteria.where("companyNumber").is(companyNumber).and("objectionId").is(objectionId));
    }
}
