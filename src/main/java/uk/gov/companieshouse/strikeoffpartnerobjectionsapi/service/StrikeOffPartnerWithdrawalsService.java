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
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.WithdrawalKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
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
 * Event tracking state (PENDING, PUBLISHED, FAILED) is recorded against the document.</p>
 */
@Service
public class StrikeOffPartnerWithdrawalsService {

    private static final String NO_OBJECTIONS_FOR_PARTNER_ORGANISATION = "NO_OBJECTIONS_FOR_PARTNER_ORGANISATION";

    private final WithdrawalRepository withdrawalRepository;
    private final ObjectionRepository objectionRepository;
    private final WithdrawalMapper withdrawalMapper;
    private final WithdrawalKafkaProducer withdrawalKafkaProducer;
    private final CompanyValidator companyValidator;
    private final Validator validator;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param withdrawalRepository    repository for persisting and retrieving withdrawal documents
     * @param objectionRepository     repository used to verify partner objections exist before withdrawal
     * @param withdrawalMapper        mapper for converting API request/response models and MongoDB documents
     * @param withdrawalKafkaProducer Kafka producer for publishing withdrawal events
     * @param companyValidator        validator for company details
     * @param validator               Jakarta Bean Validation validator for request payloads
     */
    @Autowired
    public StrikeOffPartnerWithdrawalsService(
            WithdrawalRepository withdrawalRepository,
            ObjectionRepository objectionRepository,
            WithdrawalMapper withdrawalMapper,
            WithdrawalKafkaProducer withdrawalKafkaProducer,
            CompanyValidator companyValidator,
            Validator validator) {
        this.withdrawalRepository = withdrawalRepository;
        this.objectionRepository = objectionRepository;
        this.withdrawalMapper = withdrawalMapper;
        this.withdrawalKafkaProducer = withdrawalKafkaProducer;
        this.companyValidator = companyValidator;
        this.validator = validator;
    }

    /**
     * Retrieves a single withdrawal by company number and withdrawal ID, enforcing organisation ownership.
     *
     * @param companyNumber       the company number the withdrawal belongs to
     * @param withdrawalId        the unique withdrawal identifier
     * @param partnerOrganisation the partner organisation making the request; must match the document's organisation
     * @return the withdrawal as an API response model
     * @throws ResponseStatusException    with HTTP 404 if no withdrawal is found for the given identifiers
     * @throws ResponseStatusException    with HTTP 403 if the caller's organisation does not match the document
     * @throws WithdrawalPersistenceException if a database error occurs during retrieval
     */
    public WithdrawAllObjectionsResponse getWithdrawal(
            String companyNumber,
            String withdrawalId,
            String partnerOrganisation) {

        LOGGER.info(format("Retrieving withdrawal: companyNumber=%s, withdrawalId=%s",
                companyNumber, withdrawalId));

        try {
            WithdrawalDocument document = withdrawalRepository
                    .findByCompanyNumberAndWithdrawalId(companyNumber, withdrawalId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            format("Withdrawal not found: withdrawalId=%s for company=%s", withdrawalId, companyNumber)));

            if (!partnerOrganisation.equals(document.getPartnerOrganisation())) {
                LOGGER.error(format("Organisation mismatch: caller=%s, document=%s, withdrawalId=%s",
                        partnerOrganisation, document.getPartnerOrganisation(), withdrawalId));
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access denied: withdrawal belongs to a different organisation");
            }

            LOGGER.info(format("Withdrawal retrieved successfully: withdrawalId=%s, companyNumber=%s",
                    document.getWithdrawalId(), document.getCompanyNumber()));

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
     * @throws CompanyValidationException     if company validation fails
     * @throws CompanyValidationException     if the partner has no existing objections for the company
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

        LOGGER.info(format("Creating withdrawal: companyNumber=%s, withdrawalId=%s",
                companyNumber, withdrawalId));

        WithdrawalDocument document = withdrawalMapper.toWithdrawalDocument(
                request, companyNumber, partnerOrganisation, withdrawalId, etag);
        EventTracker.markPending(document);

        try {
            WithdrawalDocument persistedWithdrawal = withdrawalRepository.insert(document);
            LOGGER.info(format("Withdrawal created successfully: withdrawalId=%s, companyNumber=%s",
                    persistedWithdrawal.getWithdrawalId(), persistedWithdrawal.getCompanyNumber()));

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
            LOGGER.info(format("Withdrawal event published successfully: withdrawalId=%s",
                    persistedWithdrawal.getWithdrawalId()));
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
            LOGGER.error(format("Failed to update withdrawal event status: withdrawalId=%s",
                    persistedWithdrawal.getWithdrawalId()), saveEx);
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
            LOGGER.info(format("No objections found for companyNumber=%s and partnerOrganisation=%s",
                    companyNumber, partnerOrganisation));
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
     * No state-transition enforcement is applied for withdrawal status updates.</p>
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

        LOGGER.info(format("Attempting to update withdrawal processing status: withdrawalId=%s, companyNumber=%s",
                withdrawalId, companyNumber));

        WithdrawalDocument existingDocument = withdrawalRepository
                .findByCompanyNumberAndWithdrawalId(companyNumber, withdrawalId)
                .orElseThrow(() -> new WithdrawalNotFoundException(
                        format("Withdrawal not found for company number=%s, withdrawalId=%s", companyNumber, withdrawalId)));

        Set<ConstraintViolation<UpdateWithdrawalStatusRequest>> violations = validator.validate(updateStatusRequest);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    format("Invalid update status request: %s", violations.iterator().next().getMessage()));
        }

        WithdrawalProcessingStatus requestedStatus = updateStatusRequest.getProcessingStatus();
        WithdrawalProcessingStatus currentStatus = parseCurrentStatus(existingDocument.getProcessingStatus());
        if (currentStatus == requestedStatus) {
            return;
        }

        existingDocument.setProcessingStatus(requestedStatus.getValue());
        existingDocument.setEtag(UUID.randomUUID().toString());

        try {
            WithdrawalDocument updatedWithdrawal = withdrawalRepository.save(existingDocument);
            LOGGER.info(format("Withdrawal processing status updated successfully: withdrawalId=%s, companyNumber=%s",
                    updatedWithdrawal.getWithdrawalId(), updatedWithdrawal.getCompanyNumber()));
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException("Failed to persist updated withdrawal processing status", ex);
        }
    }

    private static WithdrawalProcessingStatus parseCurrentStatus(String currentStatusValue) {
        if (currentStatusValue == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    format("Invalid current processing status=%s", currentStatusValue));
        }
        try {
            return WithdrawalProcessingStatus.fromValue(currentStatusValue);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    format("Invalid current processing status=%s", currentStatusValue), ex);
        }
    }
}
