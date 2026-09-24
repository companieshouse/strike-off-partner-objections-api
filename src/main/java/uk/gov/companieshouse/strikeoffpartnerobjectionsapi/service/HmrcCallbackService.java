package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResourceKind;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.HmrcCallbackPayload;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

/**
 * Service for managing HMRC outcome callback notifications with retry logic.
 *
 * <p>Orchestrates callback requests with exponential backoff retry strategy. Callbacks
 * are executed asynchronously to prevent failures from blocking the API response. Each
 * callback attempt updates the document's callback status in MongoDB for investigation
 * support. Retry attempts include enhanced logging for traceability and diagnostics.</p>
 */
@Service
public class HmrcCallbackService {

    private static final int EXECUTOR_THREAD_COUNT = 2;

    private final HmrcOutcomeCallbackClient callbackClient;
    private final ScheduledExecutorService executorService;
    private final String callbackEndpointUrl;
    private final int maxRetryAttempts;
    private final int initialDelayMillis;
    private final double backoffMultiplier;

    /**
     * Constructs the service with dependencies and configuration.
     *
     * @param callbackClient the HTTP client for sending callbacks
     * @param callbackEndpointUrl the HMRC callback endpoint URL from configuration
     * @param maxRetryAttempts maximum number of retry attempts for failed callbacks
     * @param initialDelayMillis initial delay in milliseconds before the first retry
     * @param backoffMultiplier multiplier applied to delay for each subsequent retry
     */
    public HmrcCallbackService(
            HmrcOutcomeCallbackClient callbackClient,
            @Value("${hmrc.callback.endpoint.url:}") String callbackEndpointUrl,
            @Value("${hmrc.callback.max-retry-attempts:3}") int maxRetryAttempts,
            @Value("${hmrc.callback.initial-delay-millis:1000}") int initialDelayMillis,
            @Value("${hmrc.callback.backoff-multiplier:2.0}") double backoffMultiplier) {
        this.callbackClient = callbackClient;
        this.callbackEndpointUrl = callbackEndpointUrl;
        this.maxRetryAttempts = maxRetryAttempts;
        this.initialDelayMillis = initialDelayMillis;
        this.backoffMultiplier = backoffMultiplier;
        this.executorService = Executors.newScheduledThreadPool(EXECUTOR_THREAD_COUNT);

        LOGGER.debug(format("HmrcCallbackService initialised: endpoint=%s, maxAttempts=%d, initialDelay=%dms, backoff=%.1f",
                callbackEndpointUrl.isEmpty() ? "<not-configured>" : callbackEndpointUrl,
                maxRetryAttempts, initialDelayMillis, backoffMultiplier));
    }

    /**
     * Sends an objection processing outcome callback to HMRC asynchronously.
     *
     * <p>Constructs the callback payload and triggers an asynchronous callback with
     * retry logic. The method returns immediately; callback failures do not block the
     * API response. If a result handler is provided, it will be invoked with the
     * final outcome (success or failure) for persistence.</p>
     *
     * @param objectionId the unique objection identifier
     * @param companyNumber the company number
     * @param objectionsUri the GET endpoint for retrieving the objection
     * @param resultHandler optional handler to be invoked with callback outcome (correlationId, failureReason)
     */
    public void sendObjectionOutcomeCallback(String objectionId, String companyNumber, String objectionsUri,
                                            BiConsumer<String, String> resultHandler) {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.OBJECTION,
                objectionId,
                companyNumber,
                objectionsUri
        );
        submitCallbackWithRetry(payload, 0, resultHandler);
    }

    /**
     * Sends an objection processing outcome callback to HMRC asynchronously (without result handler).
     *
     * @param objectionId the unique objection identifier
     * @param companyNumber the company number
     * @param objectionsUri the GET endpoint for retrieving the objection
     */
    public void sendObjectionOutcomeCallback(String objectionId, String companyNumber, String objectionsUri) {
        sendObjectionOutcomeCallback(objectionId, companyNumber, objectionsUri, null);
    }

    /**
     * Sends a withdrawal processing outcome callback to HMRC asynchronously.
     *
     * <p>Constructs the callback payload and triggers an asynchronous callback with
     * retry logic. The method returns immediately; callback failures do not block the
     * API response. If a result handler is provided, it will be invoked with the
     * final outcome (success or failure) for persistence.</p>
     *
     * @param withdrawalId the unique withdrawal identifier
     * @param companyNumber the company number
     * @param withdrawalUri the GET endpoint for retrieving the withdrawal
     * @param resultHandler optional handler to be invoked with callback outcome (correlationId, failureReason)
     */
    public void sendWithdrawalOutcomeCallback(String withdrawalId, String companyNumber, String withdrawalUri,
                                            BiConsumer<String, String> resultHandler) {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.WITHDRAWAL,
                withdrawalId,
                companyNumber,
                withdrawalUri
        );
        submitCallbackWithRetry(payload, 0, resultHandler);
    }

    /**
     * Sends a withdrawal processing outcome callback to HMRC asynchronously (without result handler).
     *
     * @param withdrawalId the unique withdrawal identifier
     * @param companyNumber the company number
     * @param withdrawalUri the GET endpoint for retrieving the withdrawal
     */
    public void sendWithdrawalOutcomeCallback(String withdrawalId, String companyNumber, String withdrawalUri) {
        sendWithdrawalOutcomeCallback(withdrawalId, companyNumber, withdrawalUri, null);
    }

    /**
     * Attempts to send a callback with exponential backoff retry logic.
     *
     * <p>Executes the callback asynchronously. On failure, schedules a retry with an
     * exponentially increasing delay. After maximum retry attempts, logs the final
     * failure and invokes the result handler if provided.</p>
     *
     * @param payload the callback payload to send
     * @param attemptNumber the current attempt number (0-based)
     * @param resultHandler optional handler to invoke with callback result
     */
    private void submitCallbackWithRetry(HmrcCallbackPayload payload, int attemptNumber,
                                        BiConsumer<String, String> resultHandler) {
        executorService.execute(() -> {
            try {
                LOGGER.debug(format("Attempting HMRC callback: resourceId=%s, attempt=%d",
                        payload.getResourceId(), attemptNumber + 1));

                String correlationId = callbackClient.sendCallback(callbackEndpointUrl, payload);

                // Callback succeeded - invoke handler with success
                if (resultHandler != null) {
                    resultHandler.accept(correlationId, null);
                }
            } catch (RestClientException ex) {
                handleCallbackFailure(payload, attemptNumber, ex, resultHandler);
            } catch (Exception ex) {
                LOGGER.error(format("Unexpected error during HMRC callback: resourceId=%s, attempt=%d",
                        payload.getResourceId(), attemptNumber + 1), ex);
                handleCallbackFailure(payload, attemptNumber, ex, resultHandler);
            }
         });
     }

     /**
      * Handles a failed callback by logging and potentially scheduling a retry.
     *
     * <p>Logs the failure with traceability information. If the maximum retry
     * attempts have not been exceeded, schedules a retry with exponential backoff.
     * Otherwise, logs the final failure and invokes the result handler if provided.</p>
     *
     * @param payload the callback payload that failed to send
     * @param attemptNumber the current attempt number (0-based)
     * @param ex the exception that caused the failure
     * @param resultHandler optional handler to invoke when retries exhausted
     */
    private void handleCallbackFailure(HmrcCallbackPayload payload, int attemptNumber, Exception ex,
                                      BiConsumer<String, String> resultHandler) {
        LOGGER.error(format("HMRC callback failed: resourceId=%s, companyNumber=%s, attempt=%d, error=%s",
                payload.getResourceId(), payload.getCompanyNumber(), attemptNumber + 1, ex.getMessage()), ex);

        if (attemptNumber < maxRetryAttempts - 1) {
            long delayMillis = calculateDelay(attemptNumber);
            LOGGER.info(format("Scheduling HMRC callback retry: resourceId=%s, nextAttempt=%d, delayMillis=%d",
                    payload.getResourceId(), attemptNumber + 2, delayMillis));

            executorService.schedule(
                    () -> submitCallbackWithRetry(payload, attemptNumber + 1, resultHandler),
                    delayMillis,
                    TimeUnit.MILLISECONDS
            );
        } else {
            LOGGER.error(format("HMRC callback exhausted all retry attempts: resourceId=%s, companyNumber=%s, "
                            + "totalAttempts=%d",
                    payload.getResourceId(), payload.getCompanyNumber(), maxRetryAttempts));

            // Invoke handler with failure
            if (resultHandler != null) {
                resultHandler.accept(null, ex.getMessage());
            }
         }
     }

     /**
      * Calculates the delay for an exponential backoff retry based on the attempt number.
     *
     * @param attemptNumber the current attempt number (0-based)
     * @return the delay in milliseconds
     */
    private long calculateDelay(int attemptNumber) {
        return (long) (initialDelayMillis * Math.pow(backoffMultiplier, attemptNumber));
    }
}
