package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

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
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

/**
 * Service responsible for managing strike-off partner objection lifecycle.
 *
 * <p>Handles creation, retrieval, and processing status updates for objection records.
 * On creation, the objection is persisted to MongoDB and a Kafka event is published.
 * Event tracking state (PENDING, PUBLISHED, FAILED) is recorded against the document.
 * On status update, an HMRC callback notification is triggered asynchronously after
 * the record is successfully updated in MongoDB.</p>
 */
@Service
public class StrikeOffPartnerObjectionService {

    private static final String OBJECTION_URI_TEMPLATE = "/company/%s/strike-off/objections/%s";
    private static final String RESOURCE_KIND_OBJECTION = "objection";
    private static final int CALLBACK_STATUS_MAX_RETRIES = 3;
    private static final long CALLBACK_STATUS_INITIAL_DELAY_MILLIS = 100;
    private static final double CALLBACK_STATUS_BACKOFF_MULTIPLIER = 2.0;

    private final ObjectionRepository objectionRepository;
    private final ObjectionRequestMapper objectionRequestMapper;
    private final ObjectionResponseMapper objectionResponseMapper;
    private final ObjectionKafkaProducer objectionKafkaProducer;
    private final CompanyValidator companyValidator;
    private final HmrcCallbackService hmrcCallbackService;

    @Autowired
    public StrikeOffPartnerObjectionService(
            ObjectionRepository objectionRepository,
            ObjectionRequestMapper objectionRequestMapper,
            ObjectionResponseMapper objectionResponseMapper,
            ObjectionKafkaProducer objectionKafkaProducer,
            CompanyValidator companyValidator,
            HmrcCallbackService hmrcCallbackService) {
        this.objectionRepository = objectionRepository;
        this.objectionRequestMapper = objectionRequestMapper;
        this.objectionResponseMapper = objectionResponseMapper;
        this.objectionKafkaProducer = objectionKafkaProducer;
        this.companyValidator = companyValidator;
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
        try {
            objectionRepository.save(persistedObjection);
        } catch (DataAccessException saveEx) {
            var logMap = new DataMap.Builder()
                    .companyNumber(persistedObjection.getCompanyNumber())
                    .resourceId(persistedObjection.getObjectionId())
                    .resourceKind(RESOURCE_KIND_OBJECTION)
                    .errorMessage("Failed to update event status after publish")
                    .build()
                    .getLogMap();
            LOGGER.error("Failed to save objection event status", saveEx, logMap);
        }
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
                .build()
                .getLogMap();
        LOGGER.info("Objection retrieved successfully", successLogMap);

        return objectionResponseMapper.toObjectionApiResponse(document);
    }

    /**
     * Updates the processing status of an existing objection, enforcing allowed state transitions.
     *
     * <p>Only the following transitions are permitted:
     * <ul>
     *   <li>OBJECTION_SUBMITTED → OBJECTION_PROCESSING</li>
     *   <li>OBJECTION_PROCESSING → OBJECTION_ACCEPTED or OBJECTION_REJECTED</li>
     * </ul>
     * Terminal statuses (ACCEPTED, REJECTED) cannot be changed.
     * After successful status update, an HMRC callback notification is triggered
     * asynchronously. Callback failures do not block the API response.</p>
     *
     * @param companyNumber       the company number the objection belongs to
     * @param objectionId         the unique objection identifier
     * @param updateStatusRequest request payload containing the desired processing status
     * @throws ObjectionNotFoundException if no objection is found for the given identifiers
     * @throws ResponseStatusException    with HTTP 409 if the status transition is not allowed
     * @throws ObjectionPersistenceException if the updated document cannot be persisted
     */
    public void updateObjectionProcessingStatus(
            String companyNumber,
            String objectionId,
            UpdateObjectionStatusRequest updateStatusRequest) throws ObjectionNotFoundException {

        var logMap = new DataMap.Builder()
                .companyNumber(companyNumber)
                .resourceId(objectionId)
                .resourceKind(RESOURCE_KIND_OBJECTION)
                .build()
                .getLogMap();
        LOGGER.info("Attempting to update objection processing status", logMap);

        ObjectionDocument existingDocument = objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)
                .orElseThrow(() -> new ObjectionNotFoundException(
                        format("Objection not found for company number=%s, objectionId=%s", companyNumber, objectionId)));

        String requestedStatusValue = updateStatusRequest.getProcessingStatus().getValue().trim();
        ObjectionProcessingStatus requestedStatus = parseRequestedStatus(requestedStatusValue);

        ObjectionProcessingStatus currentStatus = parseCurrentStatus(
                existingDocument.getProcessingStatus(),
                companyNumber,
                objectionId);
        if (currentStatus == requestedStatus) {
            var unchangedLogMap = new DataMap.Builder()
                    .companyNumber(companyNumber)
                    .resourceId(objectionId)
                    .resourceKind(RESOURCE_KIND_OBJECTION)
                    .status(currentStatus.getValue())
                    .build()
                    .getLogMap();
            LOGGER.debugContext(null, "Objection processing status unchanged", unchangedLogMap);
            return;
        }

        if (!isAllowedTransition(currentStatus, requestedStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    format("Invalid status transition from %s to %s",
                            currentStatus.getValue(), requestedStatus.getValue()));
        }

        existingDocument.setProcessingStatus(requestedStatus.getValue());
        Instant statusChangedAt = Instant.now();
        existingDocument.setProcessingStatusChangedAt(statusChangedAt);
        existingDocument.setEtag(objectionRequestMapper.getEtag());

        try {
            ObjectionDocument updatedObjection = objectionRepository.save(existingDocument);
            var successLogMap = new DataMap.Builder()
                    .companyNumber(updatedObjection.getCompanyNumber())
                    .resourceId(updatedObjection.getObjectionId())
                    .resourceKind(RESOURCE_KIND_OBJECTION)
                    .status(updatedObjection.getProcessingStatus())
                    .build()
                    .getLogMap();
            LOGGER.info("Objection processing status updated", successLogMap);

            // Trigger HMRC callback asynchronously after successful MongoDB update with result handler
            String objectionsUri = format(OBJECTION_URI_TEMPLATE, companyNumber, objectionId);
            Consumer<CallbackResult> resultHandler = createObjectionCallbackResultHandler(updatedObjection, statusChangedAt);
            hmrcCallbackService.sendObjectionOutcomeCallback(objectionId, companyNumber, objectionsUri, resultHandler);
        } catch (DataAccessException ex) {
            throw new ObjectionPersistenceException("Failed to persist updated objection processing status", ex);
        }
    }

    /**
     * Creates a result handler for objection callbacks that persists the callback status to MongoDB.
     *
     * <p>Reloads the document from MongoDB before updating callback status to mitigate against
     * concurrent modifications during asynchronous callback processing. Implements retry logic
     * with exponential backoff to handle transient persistence failures.</p>
     *
     * @param document the objection document to update
     * @param callbackStatusChangedAt the timestamp when the status update was initiated
     * @return a Consumer that updates and persists callback status
     */
     private Consumer<CallbackResult> createObjectionCallbackResultHandler(ObjectionDocument document, Instant callbackStatusChangedAt) {
         return callbackResult -> {
            // Reload document from MongoDB to mitigate concurrent modifications
            ObjectionDocument freshDocument = objectionRepository
                    .findByCompanyNumberAndObjectionId(
                            document.getCompanyNumber(),
                            document.getObjectionId())
                    .orElseThrow(() -> new ObjectionNotFoundException(
                            format("Objection not found: objectionId=%s", document.getObjectionId())));

            if (callbackResult.isSuccess()) {
                // Callback succeeded
                CallbackStatusTracker.markCallbackSuccess(freshDocument, callbackResult.getCorrelationId(), callbackStatusChangedAt);
                var logMap = new DataMap.Builder()
                        .companyNumber(freshDocument.getCompanyNumber())
                        .resourceId(freshDocument.getObjectionId())
                        .resourceKind(RESOURCE_KIND_OBJECTION)
                        .retryCount(callbackResult.getAttemptNumber())
                        .correlationId(callbackResult.getCorrelationId())
                        .build()
                        .getLogMap();
                LOGGER.info("HMRC callback succeeded for objection", logMap);
            } else {
                // Callback failed after all retries
                CallbackStatusTracker.markCallbackFailed(freshDocument, null, callbackResult.getFailureReason(), callbackStatusChangedAt);
                var logMap = new DataMap.Builder()
                        .companyNumber(freshDocument.getCompanyNumber())
                        .resourceId(freshDocument.getObjectionId())
                        .resourceKind(RESOURCE_KIND_OBJECTION)
                        .retryCount(callbackResult.getAttemptNumber())
                        .errorMessage(callbackResult.getFailureReason())
                        .build()
                        .getLogMap();
                LOGGER.error("HMRC callback failed permanently for objection", logMap);
            }
            persistCallbackStatusWithRetry(freshDocument, freshDocument.getObjectionId());
        };
     }

    /**
     * Persists callback status to MongoDB with retry logic and exponential backoff.
     *
     * <p>Attempts to save the document up to CALLBACK_STATUS_MAX_RETRIES times, with exponential
     * backoff between attempts. This ensures transient database failures do not result in lost
     * callback outcomes.</p>
     *
     * @param document the objection document to persist
     * @param objectionId the objection ID for logging
     * @throws ObjectionPersistenceException if persistence fails after all retry attempts
     */
     private void persistCallbackStatusWithRetry(ObjectionDocument document, String objectionId) {
         DataAccessException lastException = null;

         for (int attempt = 0; attempt < CALLBACK_STATUS_MAX_RETRIES; attempt++) {
             try {
                 objectionRepository.save(document);
                 var logMap = new DataMap.Builder()
                         .companyNumber(document.getCompanyNumber())
                         .resourceId(objectionId)
                         .resourceKind(RESOURCE_KIND_OBJECTION)
                         .retryCount(attempt + 1)
                         .build()
                         .getLogMap();
                 LOGGER.info("Callback status persisted to MongoDB", logMap);
                 return;
             } catch (DataAccessException ex) {
                 lastException = ex;
                 if (attempt < CALLBACK_STATUS_MAX_RETRIES - 1) {
                     long delayMillis = (long) (CALLBACK_STATUS_INITIAL_DELAY_MILLIS * Math.pow(CALLBACK_STATUS_BACKOFF_MULTIPLIER, attempt));
                     var logMap = new DataMap.Builder()
                             .companyNumber(document.getCompanyNumber())
                             .resourceId(objectionId)
                             .resourceKind(RESOURCE_KIND_OBJECTION)
                             .retryCount(attempt + 1)
                             .durationMs(delayMillis)
                             .build()
                             .getLogMap();
                     LOGGER.info("Callback status persistence failed, retrying", logMap);
                     try {
                         Thread.sleep(delayMillis);
                     } catch (InterruptedException ie) {
                         var errorLogMap = new DataMap.Builder()
                                 .companyNumber(document.getCompanyNumber())
                                 .resourceId(objectionId)
                                 .resourceKind(RESOURCE_KIND_OBJECTION)
                                 .errorMessage("Interrupted during callback status persistence retry")
                                 .build()
                                 .getLogMap();
                         LOGGER.error("Interrupted whilst waiting for callback status persistence retry", ie, errorLogMap);
                         Thread.currentThread().interrupt();
                         throw new ObjectionPersistenceException("Failed to persist callback status: interrupted during retry", ie);
                     }
                 }
             }
         }

         // All retries exhausted
         var logMap = new DataMap.Builder()
                 .companyNumber(document.getCompanyNumber())
                 .resourceId(objectionId)
                 .resourceKind(RESOURCE_KIND_OBJECTION)
                 .retryCount(CALLBACK_STATUS_MAX_RETRIES)
                 .errorMessage("All retry attempts exhausted")
                 .build()
                 .getLogMap();
         LOGGER.error("Failed to persist callback status after all retries", lastException, logMap);
         throw new ObjectionPersistenceException(format(
                 "Failed to persist callback status after %d retries for objectionId=%s",
                 CALLBACK_STATUS_MAX_RETRIES, objectionId), lastException);
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
