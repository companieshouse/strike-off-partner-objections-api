package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.UpdateObjectionStatusRequest;
import uk.gov.companieshouse.logging.util.DataMap;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.ObjectionKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionRequestMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionResponseMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

import static java.lang.String.format;
import static uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus.OBJECTION_ACCEPTED;
import static uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus.OBJECTION_PROCESSING;
import static uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus.OBJECTION_REJECTED;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.CallbackStatusTracker.isCallbackRequired;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

/**
 * Service responsible for managing strike-off partner objection lifecycle.
 *
 * <p>Handles creation, retrieval, and processing status updates for objection records.
 * On creation, the objection is persisted to MongoDB and a Kafka event is published.
 * Event tracking state (PENDING, PUBLISHED, FAILED) is recorded against the document.
 * Publish outcomes update only event-tracking fields to preserve concurrent processing updates.
 * On status update, an HMRC callback notification is triggered asynchronously after
 * the record is successfully updated in MongoDB.</p>
 */
@Service
public class StrikeOffPartnerObjectionService {

    private static final String OBJECTION_URI_TEMPLATE = "/company/%s/strike-off-partner-objections/%s";
    private static final String RESOURCE_KIND_OBJECTION = "objection";

    private final ObjectionRepository objectionRepository;
    private final ObjectionRequestMapper objectionRequestMapper;
    private final ObjectionResponseMapper objectionResponseMapper;
    private final ObjectionKafkaProducer objectionKafkaProducer;
    private final CompanyValidator companyValidator;
    private final EmailValidator emailValidator;
    private final HmrcCallbackService hmrcCallbackService;

    @Autowired
    public StrikeOffPartnerObjectionService(
            ObjectionRepository objectionRepository,
            ObjectionRequestMapper objectionRequestMapper,
            ObjectionResponseMapper objectionResponseMapper,
            ObjectionKafkaProducer objectionKafkaProducer,
            CompanyValidator companyValidator,
            EmailValidator emailValidator,
            HmrcCallbackService hmrcCallbackService) {
        this.objectionRepository = objectionRepository;
        this.objectionRequestMapper = objectionRequestMapper;
        this.objectionResponseMapper = objectionResponseMapper;
        this.objectionKafkaProducer = objectionKafkaProducer;
        this.companyValidator = companyValidator;
        this.emailValidator = emailValidator;
        this.hmrcCallbackService = hmrcCallbackService;
    }

    /**
     * Creates and persists a new objection, then publishes a Kafka event.
     *
     * <p>Validates the company before persisting. On successful persistence, a Kafka event
     * is published and the event status is updated to PUBLISHED or FAILED accordingly.</p>
     *
     * @param companyNumber          the company number the objection is being raised against
     * @param createObjectionRequest request payload containing the objection details
     * @param partnerOrganisation    the partner organisation submitting the objection
     * @return the created objection as an API response model
     * @throws uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException if company
     * validation fails
     * @throws ObjectionPersistenceException   if the objection cannot be persisted to MongoDB
     * @throws KafkaPublishException           if the Kafka event fails to publish
     */
    public BaseObjectionResponse createObjection(String companyNumber,
                                                 CreateObjectionRequest createObjectionRequest,
                                                 String partnerOrganisation) {

        // Validate company before persistence and publishing.
        // This validator is intentionally exception-driven: it returns nothing on success
        // and throws a CompanyValidationException on failure to stop processing.
        companyValidator.validateCompany(companyNumber, createObjectionRequest.getSubmissionCompanyName());

        String objectionId = UUID.randomUUID().toString();

        var logMap = new DataMap.Builder()
                .companyNumber(companyNumber)
                .resourceId(objectionId)
                .resourceKind(RESOURCE_KIND_OBJECTION)
                .partnerOrganisation(partnerOrganisation)
                .build()
                .getLogMap();
        LOGGER.info("Creating objection for company", logMap);

        ObjectionDocument document = objectionRequestMapper.toObjectionDocument(
                createObjectionRequest,
                companyNumber,
                partnerOrganisation,
                objectionId
        );

        // Validate email address before persistence and publishing.
        // This validator is intentionally exception-driven: it returns nothing on success
        // and throws a EmailValidationException on failure to stop processing.
        emailValidator.validateEmail(document.getPartnerContactEmail());

        EventTracker.markPending(document);

        try {
            ObjectionDocument persistedObjection = objectionRepository.insert(document);
            var successLogMap = new DataMap.Builder()
                    .companyNumber(persistedObjection.getCompanyNumber())
                    .resourceId(persistedObjection.getObjectionId())
                    .resourceKind(RESOURCE_KIND_OBJECTION)
                    .build()
                    .getLogMap();
            LOGGER.info("Objection created and persisted", successLogMap);

            publishAndSaveObjection(persistedObjection);

            return objectionResponseMapper.toObjectionApiResponse(persistedObjection);
        } catch (DataAccessException ex) {
            throw new ObjectionPersistenceException("Failed to persist objection", ex);
        }
    }

    private void publishAndSaveObjection(ObjectionDocument persistedObjection) {
        try {
            StrikeOffPartnerObjections publishedEvent = objectionKafkaProducer.publishObjectionEvent(persistedObjection);
            EventTracker.markPublished(persistedObjection, publishedEvent.getEventId());
            var logMap = new DataMap.Builder()
                    .companyNumber(persistedObjection.getCompanyNumber())
                    .resourceId(persistedObjection.getObjectionId())
                    .resourceKind(RESOURCE_KIND_OBJECTION)
                    .build()
                    .getLogMap();
            LOGGER.info("Objection event published to Kafka", logMap);
        } catch (KafkaPublishException ex) {
            EventTracker.markFailed(persistedObjection, ex.getEventId(), ex.getMessage());
            throw ex;
        } finally {
            saveObjectionEventStatus(persistedObjection);
        }
    }

    private void saveObjectionEventStatus(ObjectionDocument persistedObjection) {
        var logMap = new DataMap.Builder()
                .companyNumber(persistedObjection.getCompanyNumber())
                .resourceId(persistedObjection.getObjectionId())
                .resourceKind(RESOURCE_KIND_OBJECTION)
                .status(Objects.toString(persistedObjection.getEventStatus(), null))
                .correlationId(persistedObjection.getEventCorrelationId())
                .build()
                .getLogMap();
        try {
            UpdateResult result = objectionRepository.updateEventStatus(
                    persistedObjection.getCompanyNumber(),
                    persistedObjection.getObjectionId(),
                    persistedObjection.getEventStatus(),
                    persistedObjection.getEventStatusChangedAt(),
                    persistedObjection.getEventCorrelationId(),
                    persistedObjection.getEventFailureReason());
            logEventStatusUpdateResult(result, logMap);
        } catch (DataAccessException saveEx) {
            logMap.put("error_message", "Failed to update event status after publish");
            LOGGER.error("Failed to save objection event status", saveEx, logMap);
        }
    }

    private void logEventStatusUpdateResult(UpdateResult result, Map<String, Object> logMap) {
        if (result == null) {
            LOGGER.error("Objection event status update outcome unavailable", logMap);
            return;
        }
        logMap.put("acknowledged", result.wasAcknowledged());
        if (!result.wasAcknowledged()) {
            LOGGER.error("Objection event status update was not acknowledged", logMap);
            return;
        }
        logMap.put("matched_count", result.getMatchedCount());
        logMap.put("modified_count", result.getModifiedCount());
        if (result.getMatchedCount() == 0) {
            LOGGER.error("Objection event status update matched no document", logMap);
            return;
        }
        if (result.getModifiedCount() == 0) {
            LOGGER.info("Objection event status unchanged", logMap);
            return;
        }
        LOGGER.info("Objection event status updated", logMap);
    }

    /**
     * Retrieves a single objection by company number and objection ID, enforcing organisation ownership.
     *
     * @param companyNumber       the company number the objection belongs to
     * @param objectionId         the unique objection identifier
     * @param partnerOrganisation the partner organisation making the request; must match the document's organisation
     * @return the objection as an API response model
     * @throws ObjectionNotFoundException if no objection is found for the given company number and objection ID
     * @throws ResponseStatusException    with HTTP 403 if the caller's organisation does not match the document
     */
    public BaseObjectionResponse getObjection(String companyNumber,
                                              String objectionId,
                                              String partnerOrganisation) throws ObjectionNotFoundException {

        var logMap = new DataMap.Builder()
                .companyNumber(companyNumber)
                .resourceId(objectionId)
                .resourceKind(RESOURCE_KIND_OBJECTION)
                .build()
                .getLogMap();
        LOGGER.info("Retrieving objection", logMap);

        ObjectionDocument document = objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)
                .orElseThrow(() -> new ObjectionNotFoundException(
                        format("Objection not found for company number=%s, objectionId=%s", companyNumber, objectionId)));

        if (!partnerOrganisation.equals(document.getPartnerOrganisation())) {
            var errorLogMap = new DataMap.Builder()
                    .companyNumber(companyNumber)
                    .resourceId(objectionId)
                    .resourceKind(RESOURCE_KIND_OBJECTION)
                    .errorMessage("Organisation mismatch: caller organisation does not match document organisation")
                    .build()
                    .getLogMap();
            LOGGER.error("Organisation access control violation", errorLogMap);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: objection belongs to a different organisation");
        }

        var successLogMap = new DataMap.Builder()
                .companyNumber(document.getCompanyNumber())
                .resourceId(document.getObjectionId())
                .resourceKind(RESOURCE_KIND_OBJECTION)
                .status(document.getProcessingStatus())
                .correlationId(document.getEventCorrelationId())
                .build()
                .getLogMap();
        LOGGER.info("Objection retrieved successfully", successLogMap);

        return objectionResponseMapper.toObjectionApiResponse(document);
    }


    /**
     * Updates the processing status of an existing objection and triggers an HMRC callback if applicable.
     *
     * <p>If the requested status matches the current status, the update is silently ignored.
     * No state-transition enforcement is applied for objection status updates.
     * After successful status update, an HMRC callback will be triggered.</p>
     *
     * @param companyNumber          the company number the objection belongs to
     * @param objectionId            the unique objection identifier
     * @param updateStatusRequest    request payload containing the new processing status
     * @throws ObjectionNotFoundException if no objection is found for the given company number and objection ID
     * @throws ObjectionPersistenceException if the objection cannot be updated in MongoDB
     */
    public void updateObjectionProcessingStatus(
            String companyNumber,
            String objectionId,
            UpdateObjectionStatusRequest updateStatusRequest) throws ObjectionNotFoundException {

        ObjectionDocument existingDocument = objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)
                .orElseThrow(() -> new ObjectionNotFoundException(
                        format("Objection not found for company number=%s, objectionId=%s", companyNumber, objectionId)));

        var logMap = new DataMap.Builder()
                .companyNumber(companyNumber)
                .resourceId(objectionId)
                .resourceKind(RESOURCE_KIND_OBJECTION)
                .correlationId(existingDocument.getEventCorrelationId())
                .build()
                .getLogMap();

        String requestedStatusValue = updateStatusRequest.getProcessingStatus().getValue().trim();
        logMap.put("current_status", existingDocument.getProcessingStatus());
        logMap.put("requested_status", requestedStatusValue);
        LOGGER.info("Attempting to update objection processing status", logMap);
        ObjectionProcessingStatus requestedStatus = parseRequestedStatus(requestedStatusValue);
        ObjectionProcessingStatus currentStatus = parseCurrentStatus(
                existingDocument.getProcessingStatus(), companyNumber, objectionId);

        // If the requested status is the same as the current status, we may still need to send a callback if the current status is accepted but the callback previously failed.
        if (currentStatus == requestedStatus) {
            logMap.put("status", currentStatus.getValue());
            LOGGER.debugContext(null, "Objection processing status unchanged", logMap);
            if (isObjectionProcessed(currentStatus) && isCallbackRequired(existingDocument)) {
                sendAndPersistObjectionCallback(existingDocument);
            }
            return;
        }

        if (!isAllowedTransition(currentStatus, requestedStatus)) {
            LOGGER.error("Objection processing status transition rejected", logMap);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    format("Invalid status transition from %s to %s",
                            currentStatus.getValue(), requestedStatus.getValue()));
        }

        ObjectionDocument updatedDocument = saveProcessingStatus(existingDocument, requestedStatus);

        // Send HMRC callback only for terminal states (accepted or rejected)
        if (isObjectionProcessed(requestedStatus)) {
            sendAndPersistObjectionCallback(updatedDocument);
        }
    }

    private boolean isObjectionProcessed(ObjectionProcessingStatus currentStatus) {
        return currentStatus == OBJECTION_ACCEPTED || currentStatus == OBJECTION_REJECTED;
    }

    private ObjectionDocument saveProcessingStatus(
            ObjectionDocument document,
            ObjectionProcessingStatus requestedStatus) {

        document.setProcessingStatus(requestedStatus.getValue());
        document.setProcessingStatusChangedAt(Instant.now());
        document.setEtag(objectionRequestMapper.getEtag());

        try {
            ObjectionDocument savedDocument = objectionRepository.save(document);

            var logMap = new DataMap.Builder()
                    .companyNumber(savedDocument.getCompanyNumber())
                    .resourceId(savedDocument.getObjectionId())
                    .resourceKind(RESOURCE_KIND_OBJECTION)
                    .status(savedDocument.getProcessingStatus())
                    .build()
                    .getLogMap();

            LOGGER.info("Objection processing status updated", logMap);

            return savedDocument;
        } catch (DataAccessException ex) {
            throw new ObjectionPersistenceException(
                    "Failed to persist updated objection processing status", ex);
        }
    }

    private void sendAndPersistObjectionCallback(ObjectionDocument document) {
        String objectionsUri = format(
                OBJECTION_URI_TEMPLATE,
                document.getCompanyNumber(),
                document.getObjectionId());

        CallbackResult result = hmrcCallbackService.sendObjectionOutcomeCallback(
                document.getObjectionId(),
                document.getCompanyNumber(),
                objectionsUri);

        try {
            CallbackStatusTracker.applyResult(document, result);
            objectionRepository.save(document);
            CallbackStatusTracker.throwIfCallbackFailed(result);
        } catch (DataAccessException ex) {
            throw new ObjectionPersistenceException(
                    "Failed to persist HMRC callback outcome for objection", ex);
        }
    }

    private static ObjectionProcessingStatus parseRequestedStatus(String requestedStatusValue) {
        try {
            return ObjectionProcessingStatus.fromValue(requestedStatusValue);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    format("Unsupported status=%s", requestedStatusValue), ex);
        }
    }

    private static ObjectionProcessingStatus parseCurrentStatus(String currentStatusValue,
                                                                String companyNumber,
                                                                String objectionId) {
        try {
            return ObjectionProcessingStatus.fromValue(currentStatusValue);
        } catch (IllegalArgumentException | NullPointerException ex) {
            var logMap = new DataMap.Builder()
                    .companyNumber(companyNumber)
                    .resourceId(objectionId)
                    .resourceKind(RESOURCE_KIND_OBJECTION)
                    .status(currentStatusValue)
                    .errorMessage("Invalid persisted objection processing status")
                    .build()
                    .getLogMap();
            LOGGER.error("Unable to parse current objection status", ex, logMap);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to process objection status update");
        }
    }

    private static boolean isAllowedTransition(ObjectionProcessingStatus currentStatus,
                                               ObjectionProcessingStatus requestedStatus) {
        return switch (currentStatus) {
            case OBJECTION_SUBMITTED -> requestedStatus == OBJECTION_PROCESSING;
            case OBJECTION_PROCESSING -> requestedStatus == OBJECTION_ACCEPTED || requestedStatus == OBJECTION_REJECTED;
            case OBJECTION_ACCEPTED, OBJECTION_REJECTED -> false;
        };
    }
}
