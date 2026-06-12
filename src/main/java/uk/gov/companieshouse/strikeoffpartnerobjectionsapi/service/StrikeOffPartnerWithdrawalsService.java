package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponseLinks;
import uk.gov.companieshouse.api.objections.model.WithdrawalRequestedStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalLinks;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

import java.time.ZoneOffset;
import java.util.UUID;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class StrikeOffPartnerWithdrawalsService {

    private static final String INITIAL_WITHDRAWAL_STATUS =
            WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED.getValue();
    private static final String WITHDRAWAL_KIND = "strike-off-partner-objection#withdrawal";
    //TODO: partnerOrganisation will be retrieved from API key // NOSONAR
    private static final String PARTNER_ORGANISATION = "hmrc";

    private final WithdrawalRepository withdrawalRepository;

    public StrikeOffPartnerWithdrawalsService(final WithdrawalRepository withdrawalRepository) {
        this.withdrawalRepository = withdrawalRepository;
    }

    public WithdrawAllObjections201Response withdrawAllObjections(
            final String companyNumber,
            final WithdrawAllObjectionsRequest request) {

        final String withdrawalId = UUID.randomUUID().toString();
        final String etag = UUID.randomUUID().toString();

        LOGGER.info(format("Creating withdrawal: companyNumber=%s, withdrawalId=%s",
                companyNumber, withdrawalId));

        WithdrawalDocument document = buildWithdrawalDocument(companyNumber, withdrawalId, etag, request);

        try {
            WithdrawalDocument saved = withdrawalRepository.insert(document);
            LOGGER.info(format("Withdrawal created successfully: withdrawalId=%s, companyNumber=%s",
                    saved.getWithdrawalId(), saved.getCompanyNumber()));
            return mapToResponse(saved);
        } catch (DataAccessException ex) {
            throw new WithdrawalPersistenceException("Failed to persist withdrawal", ex);
        }
    }

    private WithdrawalDocument buildWithdrawalDocument(
            String companyNumber,
            String withdrawalId,
            String etag,
            WithdrawAllObjectionsRequest request) {

        WithdrawalDocument document = new WithdrawalDocument();
        document.setCompanyNumber(companyNumber);
        document.setWithdrawalId(withdrawalId);
        document.setSubmissionCompanyName(request.getSubmissionCompanyName());
        document.setPartnerContactEmail(request.getPartnerContactEmail());
        document.setPartnerCaseReference(request.getPartnerCaseReference());

        PartnerObjectionWorkstream workstream = request.getPartnerObjectionWorkstream();
        document.setPartnerObjectionWorkstream(workstream.getValue());

        document.setPartnerOrganisation(PARTNER_ORGANISATION);
        document.setProcessingStatus(INITIAL_WITHDRAWAL_STATUS);
        document.setEtag(etag);
        document.setKind(WITHDRAWAL_KIND);

        WithdrawalLinks links = new WithdrawalLinks();
        links.setSelf(String.format("/company/%s/strike-off-partner-objections-withdrawals/%s",
                companyNumber, withdrawalId));
        links.setCompanyProfile(String.format("/company/%s", companyNumber));
        document.setLinks(links);

        return document;
    }

    private WithdrawAllObjections201Response mapToResponse(WithdrawalDocument saved) {
        WithdrawAllObjections201Response response = new WithdrawAllObjections201Response();
        response.setCompanyNumber(saved.getCompanyNumber());
        response.setSubmissionCompanyName(saved.getSubmissionCompanyName());
        response.setWithdrawalId(saved.getWithdrawalId());
        response.setPartnerContactEmail(saved.getPartnerContactEmail());
        response.setPartnerCaseReference(saved.getPartnerCaseReference());

        if (saved.getPartnerObjectionWorkstream() != null) {
            response.setPartnerObjectionWorkstream(
                    PartnerObjectionWorkstream.fromValue(saved.getPartnerObjectionWorkstream()));
        }

        response.setCreatedAt(saved.getCreatedAt() != null
                ? saved.getCreatedAt().atOffset(ZoneOffset.UTC)
                : null);
        response.setKind(saved.getKind());
        response.setEtag(saved.getEtag());
        response.setProcessingStatus(WithdrawalRequestedStatus.fromValue(saved.getProcessingStatus()));

        if (saved.getLinks() != null) {
            response.setLinks(new WithdrawAllObjectionsResponseLinks()
                    .self(saved.getLinks().getSelf())
                    .companyProfile(saved.getLinks().getCompanyProfile()));
        }

        return response;
    }
}
