package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

import java.util.UUID;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class StrikeOffPartnerWithdrawalsService {

    //TODO: partnerOrganisation will be retrieved from API key // NOSONAR
    private static final String PARTNER_ORGANISATION = "hmrc";

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalMapper withdrawalMapper;

    public StrikeOffPartnerWithdrawalsService(
            final WithdrawalRepository withdrawalRepository,
            final WithdrawalMapper withdrawalMapper) {
        this.withdrawalRepository = withdrawalRepository;
        this.withdrawalMapper = withdrawalMapper;
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

    public WithdrawAllObjections201Response withdrawAllObjections(
            final String companyNumber,
            final WithdrawAllObjectionsRequest request) {

        final String withdrawalId = UUID.randomUUID().toString();
        final String etag = UUID.randomUUID().toString();

        LOGGER.info(format("Creating withdrawal: companyNumber=%s, withdrawalId=%s",
                companyNumber, withdrawalId));

        WithdrawalDocument document = withdrawalMapper.toWithdrawalDocument(
                request, companyNumber, PARTNER_ORGANISATION, withdrawalId, etag);

        try {
            WithdrawalDocument saved = withdrawalRepository.insert(document);
            LOGGER.info(format("Withdrawal created successfully: withdrawalId=%s, companyNumber=%s",
                    saved.getWithdrawalId(), saved.getCompanyNumber()));
            return withdrawalMapper.toWithdrawAllObjections201Response(saved);
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException("Failed to persist withdrawal", ex);
        }
    }
}
