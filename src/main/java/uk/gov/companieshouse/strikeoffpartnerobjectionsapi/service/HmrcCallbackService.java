package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.api.objections.model.CallbackResourceKind;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.HmrcCallbackPayload;

/**
 * Service for sending callbacks to HMRC regarding the outcome of objections and withdrawals.
 */
@Service
public class HmrcCallbackService {

    private final HmrcOutcomeCallbackClient callbackClient;
    private final String callbackEndpointUrl;

    public HmrcCallbackService(
            HmrcOutcomeCallbackClient callbackClient,
            @Value("${hmrc.callback.endpoint.url:}") String callbackEndpointUrl) {
        this.callbackClient = callbackClient;
        this.callbackEndpointUrl = callbackEndpointUrl;
    }

    public CallbackResult sendObjectionOutcomeCallback(String objectionId, String companyNumber, String objectionsUri) {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.STRIKE_OFF_PARTNER_OBJECTION_OBJECTION,
                objectionId,
                companyNumber,
                objectionsUri
        );
        return sendCallback(payload);
    }

    public CallbackResult sendWithdrawalOutcomeCallback(String withdrawalId, String companyNumber, String withdrawalUri) {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.STRIKE_OFF_PARTNER_OBJECTION_WITHDRAWAL,
                withdrawalId,
                companyNumber,
                withdrawalUri
        );
        return sendCallback(payload);
    }

    private CallbackResult sendCallback(HmrcCallbackPayload payload) {
        try {
            String correlationId = callbackClient.sendCallback(callbackEndpointUrl, payload);
            return CallbackResult.success(correlationId);
        } catch (Exception ex) {
            String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return CallbackResult.failure(reason);
        }
    }
}

