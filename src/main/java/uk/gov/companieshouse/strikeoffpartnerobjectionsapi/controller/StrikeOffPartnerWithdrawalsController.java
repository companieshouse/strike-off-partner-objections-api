package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.companieshouse.api.objections.api.StrikeOffPartnerWithdrawalsInterface;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerWithdrawalsService;

@RestController
public class StrikeOffPartnerWithdrawalsController implements StrikeOffPartnerWithdrawalsInterface {

    private final StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    public StrikeOffPartnerWithdrawalsController(
            final StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService) {
        this.strikeOffPartnerWithdrawalsService = strikeOffPartnerWithdrawalsService;
    }

    @Override
    public ResponseEntity<WithdrawAllObjectionsResponse> getAllWithdrawals(
            final String companyNumber, final String withdrawalId) {
        WithdrawAllObjectionsResponse response = strikeOffPartnerWithdrawalsService
                .getWithdrawal(companyNumber, withdrawalId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<WithdrawAllObjectionsResponse> withdrawAllObjections(
            final String companyNumber,
            final WithdrawAllObjectionsRequest withdrawAllObjectionsRequest) {
        WithdrawAllObjectionsResponse response = strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                companyNumber, withdrawAllObjectionsRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
