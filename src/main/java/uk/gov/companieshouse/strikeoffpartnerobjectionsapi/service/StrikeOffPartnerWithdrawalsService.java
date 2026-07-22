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
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.WithdrawalKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class StrikeOffPartnerWithdrawalsService {


    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalMapper withdrawalMapper;
    private final WithdrawalKafkaProducer withdrawalKafkaProducer;
    private final CompanyValidator companyValidator;
    private final Validator validator;
    private final TemporaryAuthenticationHeaderExtractor temporaryAuthenticationHeaderExtractor;

    @Autowired
    public StrikeOffPartnerWithdrawalsService(
            WithdrawalRepository withdrawalRepository,
            WithdrawalMapper withdrawalMapper,
            WithdrawalKafkaProducer withdrawalKafkaProducer,
            CompanyValidator companyValidator,
            Validator validator,
            TemporaryAuthenticationHeaderExtractor temporaryAuthenticationHeaderExtractor) {
        this.withdrawalRepository = withdrawalRepository;
        this.withdrawalMapper = withdrawalMapper;
        this.withdrawalKafkaProducer = withdrawalKafkaProducer;
        this.companyValidator = companyValidator;
        this.validator = validator;
        this.temporaryAuthenticationHeaderExtractor = temporaryAuthenticationHeaderExtractor;
    }

    StrikeOffPartnerWithdrawalsService(
            WithdrawalRepository withdrawalRepository,
            WithdrawalMapper withdrawalMapper,
            WithdrawalKafkaProducer withdrawalKafkaProducer,
            CompanyValidator companyValidator,
            Validator validator) {
        this(withdrawalRepository,
                withdrawalMapper,
                withdrawalKafkaProducer,
                companyValidator,
                validator,
                null);
    }

    public WithdrawAllObjectionsResponse getWithdrawal(
            final String companyNumber,
            final String withdrawalId) {
        return getWithdrawal(companyNumber, withdrawalId, resolvePartnerOrganisation());
    }

    public WithdrawAllObjectionsResponse getWithdrawal(
            final String companyNumber,
            final String withdrawalId,
            final String partnerOrganisation) {

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

    public WithdrawAllObjectionsResponse withdrawAllObjections(
            final String companyNumber,
            final WithdrawAllObjectionsRequest request,
            final String partnerOrganisation) {

        final String withdrawalId = UUID.randomUUID().toString();
        final String etag = UUID.randomUUID().toString();

        LOGGER.info(format("Creating withdrawal: companyNumber=%s, withdrawalId=%s",
                companyNumber, withdrawalId));

        // Validate company before persistence and publishing.
        // This validator is intentionally exception-driven: it returns nothing on success
        // and throws a CompanyValidationException on failure to stop processing.
        companyValidator.validateCompany(companyNumber, request.getSubmissionCompanyName());

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

    public WithdrawAllObjectionsResponse withdrawAllObjections(
            final String companyNumber,
            final WithdrawAllObjectionsRequest request) {
        return withdrawAllObjections(companyNumber, request, resolvePartnerOrganisation());
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

    public void updateWithdrawalProcessingStatus(
            final String companyNumber,
            final String withdrawalId,
            final UpdateWithdrawalStatusRequest updateStatusRequest) {

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

    private WithdrawalProcessingStatus parseCurrentStatus(String currentStatusValue) {
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

    private String resolvePartnerOrganisation() {
        if (temporaryAuthenticationHeaderExtractor == null) {
            throw new IllegalStateException("AuthenticationHeaderExtractor is not configured");
        }
        String partnerOrganisation = temporaryAuthenticationHeaderExtractor.getPartnerOrganisation();
        if (partnerOrganisation == null) {
            throw new IllegalStateException("Missing partner organisation in request context");
        }
        return partnerOrganisation;
    }
}
