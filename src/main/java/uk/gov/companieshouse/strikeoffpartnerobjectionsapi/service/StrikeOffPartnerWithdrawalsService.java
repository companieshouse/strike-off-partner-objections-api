package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.WithdrawalKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class StrikeOffPartnerWithdrawalsService {

    //TODO: partnerOrganisation will be retrieved from API key // NOSONAR
    private static final String PARTNER_ORGANISATION = "hmrc";

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalMapper withdrawalMapper;
    private final WithdrawalKafkaProducer withdrawalKafkaProducer;

    public StrikeOffPartnerWithdrawalsService(
            WithdrawalRepository withdrawalRepository,
            WithdrawalMapper withdrawalMapper,
            WithdrawalKafkaProducer withdrawalKafkaProducer) {
        this.withdrawalRepository = withdrawalRepository;
        this.withdrawalMapper = withdrawalMapper;
        this.withdrawalKafkaProducer = withdrawalKafkaProducer;
    }

    public WithdrawAllObjectionsResponse getWithdrawal(
            final String companyNumber,
            final String withdrawalId) {

        LOGGER.info(format("Retrieving withdrawal: companyNumber=%s, withdrawalId=%s",
                companyNumber, withdrawalId));

        try {
            WithdrawalDocument document = withdrawalRepository
                    .findByCompanyNumberAndWithdrawalId(companyNumber, withdrawalId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            format("Withdrawal not found: withdrawalId=%s for company=%s", withdrawalId, companyNumber)));

            LOGGER.info(format("Withdrawal retrieved successfully: withdrawalId=%s, companyNumber=%s",
                    document.getWithdrawalId(), document.getCompanyNumber()));

            return withdrawalMapper.toWithdrawAllObjectionsResponse(document);
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException("Failed to retrieve withdrawal", ex);
        }
    }

    public WithdrawAllObjectionsResponse withdrawAllObjections(
            final String companyNumber,
            final WithdrawAllObjectionsRequest request) {

        final String withdrawalId = UUID.randomUUID().toString();
        final String etag = UUID.randomUUID().toString();
        final String eventCorrelationId = UUID.randomUUID().toString();

        LOGGER.info(format("Creating withdrawal: companyNumber=%s, withdrawalId=%s",
                companyNumber, withdrawalId));

        WithdrawalDocument document = withdrawalMapper.toWithdrawalDocument(
                request, companyNumber, PARTNER_ORGANISATION, withdrawalId, etag);
        setEventTrackingState(document, eventCorrelationId, EventStatus.PENDING, null);

        try {
            WithdrawalDocument saved = withdrawalRepository.insert(document);
            LOGGER.info(format("Withdrawal created successfully: withdrawalId=%s, companyNumber=%s",
                    saved.getWithdrawalId(), saved.getCompanyNumber()));
            publishAndUpdateEventState(saved, eventCorrelationId);

            return withdrawalMapper.toWithdrawAllObjectionsResponse(saved);
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException("Failed to persist withdrawal", ex);
        }
    }

    private void publishAndUpdateEventState(WithdrawalDocument saved, String eventCorrelationId) {
        try {
            withdrawalKafkaProducer.publishWithdrawalEvent(saved);
            setEventTrackingState(saved, eventCorrelationId, EventStatus.PUBLISHED, null);
            saveEventState(saved, "after publish");
            LOGGER.info(format("Withdrawal event published successfully: withdrawalId=%s", saved.getWithdrawalId()));
        } catch (KafkaPublishException ex) {
            setEventTrackingState(saved, eventCorrelationId, EventStatus.FAILED, ex.getMessage());
            saveEventState(saved, "after publish failure");
            throw ex;
        }
    }

    private void saveEventState(WithdrawalDocument saved, String context) {
        try {
            withdrawalRepository.save(saved);
        } catch (DataAccessException dae) {
            LOGGER.error(format(
                    "Failed to update withdrawal event tracking state %s: withdrawalId=%s",
                    context, saved.getWithdrawalId()), dae);
        }
    }

    private static void setEventTrackingState(
            WithdrawalDocument document,
            String eventCorrelationId,
            EventStatus eventStatus,
            String failureReason) {
        document.setEventCorrelationId(eventCorrelationId);
        document.setEventStatus(eventStatus.name());
        document.setEventStatusChangedAt(Instant.now());
        document.setEventFailureReason(failureReason);
    }
}
