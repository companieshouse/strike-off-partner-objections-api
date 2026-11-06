package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.Optional;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;

/**
 * Field-specific withdrawal updates that preserve independently written state.
 */
public interface WithdrawalUpdateRepository {

    UpdateResult updateEventStatus(String companyNumber, String withdrawalId, EventStatus status,
                                   Instant changedAt, String correlationId, String failureReason);

    Optional<WithdrawalDocument> updateProcessingStatus(
            String companyNumber, String withdrawalId, String expectedStatus, String requestedStatus,
            Instant changedAt, String etag);

    UpdateResult updateCallbackStatus(String companyNumber, String withdrawalId, CallbackStatus status,
                                      Instant changedAt, String correlationId, String failureReason);
}
