package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerRequestDocument;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

abstract class AbstractEventTrackingService {

    protected <T extends PartnerRequestDocument> void publishAndUpdateEventState(
            T persistedDocument,
            Supplier<StrikeOffPartnerObjections> publishAction,
            Consumer<T> saveAction,
            Function<T, String> idExtractor,
            String entityName,
            String idFieldName) {
        try {
            StrikeOffPartnerObjections event = publishAction.get();
            setEventTrackingState(persistedDocument, event.getEventId(), EventStatus.PUBLISHED, null);
            saveEventState(persistedDocument, saveAction, idExtractor, entityName, idFieldName, "after publish");
            LOGGER.info(format("%s event published successfully: %s=%s",
                    entityName.toUpperCase(), idFieldName, idExtractor.apply(persistedDocument)));
        } catch (KafkaPublishException ex) {
            setEventTrackingState(persistedDocument, ex.getEventId(), EventStatus.FAILED, ex.getMessage());
            saveEventState(persistedDocument, saveAction, idExtractor, entityName, idFieldName, "after publish failure");
            throw ex;
        }
    }

    protected void setEventTrackingState(
            PartnerRequestDocument document,
            String eventCorrelationId,
            EventStatus eventStatus,
            String failureReason) {
        document.setEventCorrelationId(eventCorrelationId);
        document.setEventStatus(eventStatus.name());
        document.setEventStatusChangedAt(Instant.now());
        document.setEventFailureReason(failureReason);
    }

    private <T extends PartnerRequestDocument> void saveEventState(
            T persistedDocument,
            Consumer<T> saveAction,
            Function<T, String> idExtractor,
            String entityName,
            String idFieldName,
            String context) {
        try {
            saveAction.accept(persistedDocument);
        } catch (DataAccessException ex) {
            LOGGER.error(format(
                    "Failed to update %s event tracking state %s: %s=%s",
                    entityName,
                    context,
                    idFieldName,
                    idExtractor.apply(persistedDocument)), ex);
        }
    }
}

