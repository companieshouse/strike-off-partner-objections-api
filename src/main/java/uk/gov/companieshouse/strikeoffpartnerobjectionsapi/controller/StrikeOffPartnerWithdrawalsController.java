package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.api.StrikeOffPartnerWithdrawalsInterface;
import uk.gov.companieshouse.api.objections.model.UpdateWithdrawalStatusRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerWithdrawalsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.ERIC_PARTNER_ORGANISATION_HEADER;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.validatePartnerOrganisation;

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
    private final HttpServletRequest httpServletRequest;

    /**
     * Constructs the controller with the service used to process withdrawal operations.
     *
     * @param strikeOffPartnerWithdrawalsService service handling withdrawal retrieval and creation
     * @param httpServletRequest the current HTTP request used to resolve the partner organisation header
     */
    public StrikeOffPartnerWithdrawalsController(
            final StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService,
            final HttpServletRequest httpServletRequest) {
        this.strikeOffPartnerWithdrawalsService = strikeOffPartnerWithdrawalsService;
        this.httpServletRequest = httpServletRequest;
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
        String partnerOrganisation = resolvePartnerOrganisation();
        WithdrawAllObjectionsResponse response = strikeOffPartnerWithdrawalsService
                .getWithdrawal(companyNumber, withdrawalId, partnerOrganisation);
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
        String partnerOrganisation = resolvePartnerOrganisation();
        WithdrawAllObjectionsResponse response = strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                companyNumber, withdrawAllObjectionsRequest, partnerOrganisation);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<Void> updateWithdrawalStatus(
            @Size(min = 1) @PathVariable("company_number") final String companyNumber,
            @Size(min = 1) @PathVariable("withdrawal_id") final String withdrawalId,
            @Valid @RequestBody final UpdateWithdrawalStatusRequest updateStatusRequest) {
        try {
            strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                    companyNumber,
                    withdrawalId,
                    updateStatusRequest);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (WithdrawalNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    private String resolvePartnerOrganisation() {
        String partnerOrganisation = httpServletRequest.getHeader(ERIC_PARTNER_ORGANISATION_HEADER);
        return validatePartnerOrganisation(partnerOrganisation);
    }
}
