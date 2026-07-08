package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.companieshouse.api.objections.api.StrikeOffPartnerWithdrawalsInterface;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerWithdrawalsService;

/**
 * REST controller for strike off partner withdrawal operations.
 *
 * <p>Implements {@link StrikeOffPartnerWithdrawalsInterface} to expose endpoints
 * for retrieving a specific withdrawal and creating a withdrawal for all objections
 * against a company.</p>
 */
@RestController
public class StrikeOffPartnerWithdrawalsController implements StrikeOffPartnerWithdrawalsInterface {

    private final StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    /**
     * Constructs the controller with the service used to process withdrawal operations.
     *
     * @param strikeOffPartnerWithdrawalsService service handling withdrawal retrieval and creation
     */
    public StrikeOffPartnerWithdrawalsController(
            final StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService) {
        this.strikeOffPartnerWithdrawalsService = strikeOffPartnerWithdrawalsService;
    }

    /**
     * Retrieves a single withdrawal for the provided company and withdrawal identifiers.
     *
     * @param companyNumber the company number the withdrawal belongs to
     * @param withdrawalId the unique withdrawal identifier
     * @return a response entity containing the matched withdrawal and HTTP 200 status
     */
    @Override
    public ResponseEntity<WithdrawAllObjectionsResponse> getAllWithdrawals(
            final String companyNumber, final String withdrawalId) {
        WithdrawAllObjectionsResponse response = strikeOffPartnerWithdrawalsService
                .getWithdrawal(companyNumber, withdrawalId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Withdraws all objections for the specified company.
     *
     * @param companyNumber the company number to apply the withdrawal against
     * @param withdrawAllObjectionsRequest request payload describing the withdrawal details
     * @return a response entity containing the created withdrawal record and HTTP 201 status
     */
    @Override
    public ResponseEntity<WithdrawAllObjectionsResponse> withdrawAllObjections(
            final String companyNumber,
            final WithdrawAllObjectionsRequest withdrawAllObjectionsRequest) {
        WithdrawAllObjectionsResponse response = strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                companyNumber, withdrawAllObjectionsRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
