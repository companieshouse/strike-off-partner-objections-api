package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import uk.gov.companieshouse.api.objections.model.*;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class StrikeOffPartnerWithdrawalsService {

    private static final WithdrawalRequestedStatus INITIAL_WITHDRAWAL_STATUS =
            WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED;

    public WithdrawAllObjections201Response withdrawAllObjections(final String companyNumber, final WithdrawAllObjectionsRequest withdrawAllObjectionsRequest) {
        final String withdrawalId = UUID.randomUUID().toString();
        final String selfLink = String.format(
                "/company/%s/strike-off-partner-objections-withdrawals",
                companyNumber);
        LOGGER.info(format("Creating withdrawal: companyNumber=%s, withdrawalId=%s",
                companyNumber, withdrawalId));

        WithdrawAllObjections201Response response = new WithdrawAllObjections201Response();
        response.setCompanyNumber(companyNumber);
        response.setSubmissionCompanyName(withdrawAllObjectionsRequest.getSubmissionCompanyName());
        response.setWithdrawalId(withdrawalId);
        response.setPartnerContactEmail(withdrawAllObjectionsRequest.getPartnerContactEmail());
        response.setPartnerCaseReference(withdrawAllObjectionsRequest.getPartnerCaseReference());
        response.setPartnerObjectionWorkstream(withdrawAllObjectionsRequest.getPartnerObjectionWorkstream());
        response.setCreatedAt(OffsetDateTime.now());
        response.setLinks(new WithdrawAllObjectionsResponseLinks().self(selfLink));
        response.setKind("strike-off-partner-objection#withdrawal");
        response.setEtag(UUID.randomUUID().toString());
        response.setProcessingStatus(INITIAL_WITHDRAWAL_STATUS);

        LOGGER.info(format("Withdrawal created successfully: withdrawalId=%s, companyNumber=%s", withdrawalId, companyNumber));

        return response;
    }
}
