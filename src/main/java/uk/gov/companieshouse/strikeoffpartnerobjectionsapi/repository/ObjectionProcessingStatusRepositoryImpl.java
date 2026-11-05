package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

public class ObjectionProcessingStatusRepositoryImpl implements ObjectionProcessingStatusRepository {

    private final MongoTemplate mongoTemplate;

    public ObjectionProcessingStatusRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<ObjectionDocument> updateProcessingStatus(
            String companyNumber, String objectionId, String expectedStatus, String requestedStatus,
            Instant statusChangedAt, String etag) {
        Query query = Query.query(Criteria.where("companyNumber").is(companyNumber)
                .and("objectionId").is(objectionId)
                .and("processingStatus").is(expectedStatus));
        Update update = new Update()
                .set("processingStatus", requestedStatus)
                .set("processingStatusChangedAt", statusChangedAt)
                .set("etag", etag);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ObjectionDocument.class));
    }
}
