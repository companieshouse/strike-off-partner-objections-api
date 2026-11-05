package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import java.time.Instant;
import java.util.Optional;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

public interface ObjectionProcessingStatusRepository {

    /**
     * Updates processing fields only if the persisted status still matches the expected status.
     *
     * @return the atomically updated document, or empty if the document no longer matches
     */
    Optional<ObjectionDocument> updateProcessingStatus(String companyNumber, String objectionId,
                                                      String expectedStatus, String requestedStatus,
                                                      Instant statusChangedAt, String etag);
}
