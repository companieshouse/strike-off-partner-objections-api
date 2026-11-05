package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;

public interface ObjectionEventStatusRepository {

    /**
     * Updates only event-tracking fields and returns MongoDB's write outcome.
     */
    UpdateResult updateEventStatus(String companyNumber, String objectionId, EventStatus eventStatus,
                                  Instant eventStatusChangedAt, String eventCorrelationId, String eventFailureReason);
}
