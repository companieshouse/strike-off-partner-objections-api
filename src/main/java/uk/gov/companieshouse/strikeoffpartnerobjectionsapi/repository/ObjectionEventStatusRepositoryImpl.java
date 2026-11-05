package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

public class ObjectionEventStatusRepositoryImpl implements ObjectionEventStatusRepository {

    private final MongoTemplate mongoTemplate;

    public ObjectionEventStatusRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public UpdateResult updateEventStatus(String companyNumber, String objectionId, EventStatus eventStatus,
                                         Instant eventStatusChangedAt, String eventCorrelationId,
                                         String eventFailureReason) {
        Query query = Query.query(Criteria.where("companyNumber").is(companyNumber)
                .and("objectionId").is(objectionId));
        Update update = new Update()
                .set("eventStatus", eventStatus)
                .set("eventStatusChangedAt", eventStatusChangedAt)
                .set("eventCorrelationId", eventCorrelationId)
                .set("eventFailureReason", eventFailureReason);
        return mongoTemplate.updateFirst(query, update, ObjectionDocument.class);
    }
}
