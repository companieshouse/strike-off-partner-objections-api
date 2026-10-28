package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.UpdateWithdrawalStatusRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.api.objections.model.WithdrawalProcessingStatus;
import uk.gov.companieshouse.logging.util.DataMap;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.WithdrawalKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

/**
 * Service responsible for managing strike-off partner withdrawal lifecycle.
 *
 * <p>Handles creation, retrieval, and processing status updates for withdrawal records.
 * On creation, a check is made that the partner has at least one active objection for
 * the company before the withdrawal is persisted to MongoDB and a Kafka event is published.
 * Event tracking state (PENDING, PUBLISHED, FAILED) is recorded against the document.
 * On status update, an HMRC callback notification is triggered asynchronously after
 * the record is successfully updated in MongoDB.</p>
 */
@Service
public class StrikeOffPartnerWithdrawalsService {

    private static final String NO_OBJECTIONS_FOR_PARTNER_ORGANISATION = "NO_OBJECTIONS_FOR_PARTNER_ORGANISATION";
    private static final String WITHDRAWAL_URI_TEMPLATE =
            "/company/%s/strike-off-partner-objections-withdrawals/%s";
    private static final String RESOURCE_KIND_WITHDRAWAL = "withdrawal";

    private final WithdrawalRepository withdrawalRepository;
    private final ObjectionRepository objectionRepository;
    private final WithdrawalMapper withdrawalMapper;
    private final WithdrawalKafkaProducer withdrawalKafkaProducer;
    private final CompanyValidator companyValidator;
    private final Validator validator;
    private final HmrcCallbackService hmrcCallbackService;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param withdrawalRepository    repository for persisting and retrieving withdrawal documents
     * @param objectionRepository     repository used to verify partner objections exist before withdrawal
     * @param withdrawalMapper        mapper for converting API request/response models and MongoDB documents
     * @param withdrawalKafkaProducer Kafka producer for publishing withdrawal events
     * @param companyValidator        validator for company details
     * @param validator               Jakarta Bean Validation validator for request payloads
     * @param hmrcCallbackService     service for triggering HMRC outcome callbacks
     */
    @Autowired
    public StrikeOffPartnerWithdrawalsService(
            WithdrawalRepository withdrawalRepository,
            ObjectionRepository objectionRepository,
            WithdrawalMapper withdrawalMapper,
            WithdrawalKafkaProducer withdrawalKafkaProducer,
            CompanyValidator companyValidator,
            Validator validator,
            HmrcCallbackService hmrcCallbackService) {
        this.withdrawalRepository = withdrawalRepository;
        this.objectionRepository = objectionRepository;
        this.withdrawalMapper = withdrawalMapper;
        this.withdrawalKafkaProducer = withdrawalKafkaProducer;
        this.companyValidator = companyValidator;
        this.validator = validator;
        this.hmrcCallbackService = hmrcCallbackService;
    }

    /**
     * Retrieves a single withdrawal by company number and withdrawal ID, enforcing organisation ownership.
     *
     * @param companyNumber       the company number the withdrawal belongs to
     * @param withdrawalId        the unique withdrawal identifier
     * @param partnerOrganisation the partner organisation making the request; must match the document's organisation
     * @return the withdrawal as an API response model
     * @throws ResponseStatusException    with HTTP 404 if no withdrawal is found for the given identifiers or with HTTP
     * 403 if the caller's organisation does not match the document
     * @throws WithdrawalPersistenceException if a database error occurs during retrieval
     */
    public WithdrawAllObjectionsResponse getWithdrawal(
            String companyNumber,
            String withdrawalId,
            String partnerOrganisation) {

        var logMap = new DataMap.Builder()
                .companyNumber(companyNumber)
                .resourceId(withdrawalId)
                .resourceKind(RESOURCE_KIND_WITHDRAWAL)
                .build()
                .getLogMap();
        LOGGER.info("Retrieving withdrawal", logMap);

        try {
            WithdrawalDocument document = withdrawalRepository
                    .findByCompanyNumberAndWithdrawalId(companyNumber, withdrawalId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            format("Withdrawal not found: withdrawalId=%s for company=%s", withdrawalId, companyNumber)));

            if (!partnerOrganisation.equals(document.getPartnerOrganisation())) {
                var errorLogMap = new DataMap.Builder()
                        .companyNumber(companyNumber)
                        .resourceId(withdrawalId)
                        .resourceKind(RESOURCE_KIND_WITHDRAWAL)
                        .errorMessage("Organisation mismatch: caller organisation does not match document organisation")
                        .build()
                        .getLogMap();
                LOGGER.error("Organisation access control violation", errorLogMap);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access denied: withdrawal belongs to a different organisation");
            }

            var successLogMap = new DataMap.Builder()
                    .companyNumber(document.getCompanyNumber())
                    .resourceId(document.getWithdrawalId())
                    .resourceKind(RESOURCE_KIND_WITHDRAWAL)
                    .build()
                    .getLogMap();
            LOGGER.info("Withdrawal retrieved successfully", successLogMap);

            return withdrawalMapper.toWithdrawAllObjectionsResponse(document);
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException("Failed to retrieve withdrawal", ex);
        }
    }

    /**
     * Creates and persists a new withdrawal for all objections belonging to the partner, then publishes a Kafka event.
     *
     * <p>Validates the company and confirms the partner has at least one active objection for the company
     * before persisting. On successful persistence, a Kafka event is published and the event status
     * is updated to PUBLISHED or FAILED accordingly.</p>
     *
     * @param companyNumber       the company number to apply the withdrawal against
     * @param request             request payload describing the withdrawal details
     * @param partnerOrganisation the partner organisation submitting the withdrawal
     * @return the created withdrawal as an API response model
     * @throws CompanyValidationException     if company validation fails or the partner has no existing objections
     * for the company
     * @throws WithdrawalPersistenceException if the withdrawal cannot be persisted to MongoDB
     * @throws KafkaPublishException          if the Kafka event fails to publish
     */
    public WithdrawAllObjectionsResponse withdrawAllObjections(
            String companyNumber,
            WithdrawAllObjectionsRequest request,
            String partnerOrganisation) {

        // Validate company before persistence and publishing.
        // This validator is intentionally exception-driven: it returns nothing on success
        // and throws a CompanyValidationException on failure to stop processing.
        // Company type validation is excluded for withdrawals (per acceptance criteria).
        companyValidator.validateCompanyForWithdrawal(companyNumber, request.getSubmissionCompanyName());
        validatePartnerHasObjections(companyNumber, partnerOrganisation);

        String withdrawalId = UUID.randomUUID().toString();
        String etag = UUID.randomUUID().toString();

        var logMap = new DataMap.Builder()
                .companyNumber(companyNumber)
                .resourceId(withdrawalId)
                .resourceKind(RESOURCE_KIND_WITHDRAWAL)
                .partnerOrganisation(partnerOrganisation)
                .build()
                .getLogMap();
        LOGGER.info("Creating withdrawal for company", logMap);

        WithdrawalDocument document = withdrawalMapper.toWithdrawalDocument(
                request, companyNumber, partnerOrganisation, withdrawalId, etag);
        EventTracker.markPending(document);

        try {
            WithdrawalDocument persistedWithdrawal = withdrawalRepository.insert(document);
            var successLogMap = new DataMap.Builder()
                    .companyNumber(persistedWithdrawal.getCompanyNumber())
                    .resourceId(persistedWithdrawal.getWithdrawalId())
                    .resourceKind(RESOURCE_KIND_WITHDRAWAL)
                    .build()
                    .getLogMap();
            LOGGER.info("Withdrawal created and persisted", successLogMap);

            publishAndSaveWithdrawal(persistedWithdrawal);

            return withdrawalMapper.toWithdrawAllObjectionsResponse(persistedWithdrawal);
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException("Failed to persist withdrawal", ex);
        }
    }

    private void publishAndSaveWithdrawal(WithdrawalDocument persistedWithdrawal) {
        try {
            StrikeOffPartnerObjections publishedEvent = withdrawalKafkaProducer.publishWithdrawalEvent(persistedWithdrawal);
            EventTracker.markPublished(persistedWithdrawal, publishedEvent.getEventId());
            var logMap = new DataMap.Builder()
                    .companyNumber(persistedWithdrawal.getCompanyNumber())
                    .resourceId(persistedWithdrawal.getWithdrawalId())
                    .resourceKind(RESOURCE_KIND_WITHDRAWAL)
                    .build()
                    .getLogMap();
            LOGGER.info("Withdrawal event published to Kafka", logMap);
        } catch (KafkaPublishException ex) {
            EventTracker.markFailed(persistedWithdrawal, ex.getEventId(), ex.getMessage());
            throw ex;
        } finally {
            saveWithdrawalEventStatus(persistedWithdrawal);
        }
    }

    private void saveWithdrawalEventStatus(WithdrawalDocument persistedWithdrawal) {
        try {
            withdrawalRepository.save(persistedWithdrawal);
        } catch (DataAccessException saveEx) {
            var logMap = new DataMap.Builder()
                    .companyNumber(persistedWithdrawal.getCompanyNumber())
                    .resourceId(persistedWithdrawal.getWithdrawalId())
                    .resourceKind(RESOURCE_KIND_WITHDRAWAL)
                    .errorMessage("Failed to update event status after publish")
                    .build()
                    .getLogMap();
            LOGGER.error("Failed to save withdrawal event status", saveEx, logMap);
        }
    }

    private void validatePartnerHasObjections(String companyNumber, String partnerOrganisation) {
        boolean hasObjectionsForPartner;
        try {
            hasObjectionsForPartner = objectionRepository
                    .existsByCompanyNumberAndPartnerOrganisation(companyNumber, partnerOrganisation);
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException("Failed to validate objections for withdrawal", ex);
        }
        if (!hasObjectionsForPartner) {
            var logMap = new DataMap.Builder()
                    .companyNumber(companyNumber)
                    .errorMessage("No objections found for partner organisation")
                    .build()
                    .getLogMap();
            LOGGER.info("Withdrawal validation failed: no partner objections found", logMap);
            throw new CompanyValidationException(
                    format("No objections found for companyNumber=%s and partnerOrganisation=%s",
                            companyNumber, partnerOrganisation),
                    NO_OBJECTIONS_FOR_PARTNER_ORGANISATION);
        }
    }

    /**
     * Updates the processing status of an existing withdrawal.
     *
     * <p>If the requested status matches the current status, the update is silently ignored.
     * No state-transition enforcement is applied for withdrawal status updates.
     * After successful status update, an HMRC callback will be triggered</p>
     *
     * @param companyNumber       the company number the withdrawal belongs to
     * @param withdrawalId        the unique withdrawal identifier
     * @param updateStatusRequest request payload containing the desired processing status
     * @throws WithdrawalNotFoundException    if no withdrawal is found for the given identifiers
     * @throws ResponseStatusException        with HTTP 400 if the request payload fails validation
     * @throws ResponseStatusException        with HTTP 409 if the current status value is invalid
     * @throws WithdrawalPersistenceException if the updated document cannot be persisted
     */
    public void updateWithdrawalProcessingStatus(
            String companyNumber,
            String withdrawalId,
            UpdateWithdrawalStatusRequest updateStatusRequest) {

        logProcessingStatus(companyNumber, withdrawalId, null,
                "Attempting to update withdrawal processing status");

        WithdrawalDocument document = withdrawalRepository
                .findByCompanyNumberAndWithdrawalId(companyNumber, withdrawalId)
                .orElseThrow(() -> new WithdrawalNotFoundException(
                        format("Withdrawal not found for company number=%s, withdrawalId=%s",
                                companyNumber, withdrawalId)));

        validateUpdateStatusRequest(updateStatusRequest);

        WithdrawalProcessingStatus requestedStatus = updateStatusRequest.getProcessingStatus();
        WithdrawalProcessingStatus currentStatus = parseCurrentStatus(document.getProcessingStatus());

        if (currentStatus == requestedStatus) {
            if (isWithdrawalProcessed(currentStatus) && document.getCallbackStatus() == CallbackStatus.FAILED) {
                sendAndPersistWithdrawalCallback(document);
            }
            return;
        }

        WithdrawalDocument updatedDocument = saveProcessingStatus(document, requestedStatus);
        if (isWithdrawalProcessed(requestedStatus)) {
            sendAndPersistWithdrawalCallback(updatedDocument);
        }
    }

    private void validateUpdateStatusRequest(UpdateWithdrawalStatusRequest request) {
        Set<ConstraintViolation<UpdateWithdrawalStatusRequest>> violations =
                validator.validate(request);

        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    format("Invalid update status request: %s",
                            violations.iterator().next().getMessage()));
        }
    }

    private WithdrawalDocument saveProcessingStatus(
            WithdrawalDocument document,
            WithdrawalProcessingStatus requestedStatus) {

        document.setProcessingStatus(requestedStatus.getValue());
        document.setEtag(UUID.randomUUID().toString());

        try {
            WithdrawalDocument savedDocument = withdrawalRepository.save(document);

            logProcessingStatus(
                    savedDocument.getCompanyNumber(),
                    savedDocument.getWithdrawalId(),
                    savedDocument.getProcessingStatus(),
                    "Withdrawal processing status updated");

            return savedDocument;
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException(
                    "Failed to persist updated withdrawal processing status", ex);
        }
    }

    private void sendAndPersistWithdrawalCallback(WithdrawalDocument document) {
        String withdrawalUri = format(
                WITHDRAWAL_URI_TEMPLATE,
                document.getCompanyNumber(),
                document.getWithdrawalId());

        CallbackResult result = hmrcCallbackService.sendWithdrawalOutcomeCallback(
                document.getWithdrawalId(),
                document.getCompanyNumber(),
                withdrawalUri);

        try {
            CallbackStatusTracker.applyResult(document, result);
            withdrawalRepository.save(document);
            CallbackStatusTracker.throwIfCallbackFailed(result);
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException(
                    "Failed to persist HMRC callback outcome for withdrawal", ex);
        }
    }

    private void logProcessingStatus(
            String companyNumber,
            String withdrawalId,
            String status,
            String message) {

        var builder = new DataMap.Builder()
                .companyNumber(companyNumber)
                .resourceId(withdrawalId)
                .resourceKind(RESOURCE_KIND_WITHDRAWAL);

        if (status != null) {
            builder.status(status);
        }

        LOGGER.info(message, builder.build().getLogMap());
    }

    private boolean isWithdrawalProcessed(WithdrawalProcessingStatus currentStatus) {
        return currentStatus == WithdrawalProcessingStatus.WITHDRAWAL_ACCEPTED || currentStatus == WithdrawalProcessingStatus.WITHDRAWAL_REJECTED;
    }

    private static WithdrawalProcessingStatus parseCurrentStatus(String currentStatusValue) {
        if (currentStatusValue == null) {
            var logMap = new DataMap.Builder()
                    .status(currentStatusValue)
                    .errorMessage("Invalid current processing status: null value")
                    .build()
                    .getLogMap();
            LOGGER.error("Unable to parse current withdrawal status", logMap);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    format("Invalid current processing status=%s", currentStatusValue));
        }
        try {
            return WithdrawalProcessingStatus.fromValue(currentStatusValue);
        } catch (IllegalArgumentException ex) {
            var logMap = new DataMap.Builder()
                    .status(currentStatusValue)
                    .errorMessage("Invalid withdrawal processing status value")
                    .build()
                    .getLogMap();
            LOGGER.error("Unable to parse current withdrawal status", ex, logMap);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    format("Invalid current processing status=%s", currentStatusValue), ex);
        }
    }
}
