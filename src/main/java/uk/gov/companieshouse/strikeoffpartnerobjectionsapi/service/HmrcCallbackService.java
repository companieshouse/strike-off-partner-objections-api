package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.beans.factory.DisposableBean;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackResourceKind;
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
 *
 * <p>This service is thread-safe and handles concurrent callback submissions with graceful
 * shutdown support. Synchronisation is implemented to prevent race conditions during
 * executor shutdown.</p>
 */
@Service
public class HmrcCallbackService implements DisposableBean {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final String SHUTDOWN_MESSAGE = "HmrcCallbackService is shutting down";

    private final HmrcOutcomeCallbackClient callbackClient;
    private final String callbackEndpointUrl;
    private final int maxRetryAttempts;
    private final int initialDelayMillis;
    private final double backoffMultiplier;

    // Thread-safe access to executor service
    private final AtomicReference<ScheduledExecutorService> executorService;
    private final Object executorLock = new Object();
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    /**
     * Constructs the service with dependencies and configuration.
     *
     * @param callbackClient the HTTP client for sending callbacks
     * @param callbackEndpointUrl the HMRC callback endpoint URL from configuration
     * @param maxRetryAttempts maximum number of retry attempts for failed callbacks
     * @param initialDelayMillis initial delay in milliseconds before the first retry
     * @param backoffMultiplier multiplier applied to delay for each subsequent retry
     * @param executorThreadPoolSize number of threads in the callback executor pool
     */
    public HmrcCallbackService(
            HmrcOutcomeCallbackClient callbackClient,
            @Value("${hmrc.callback.endpoint.url:}") String callbackEndpointUrl,
            @Value("${hmrc.callback.max-retry-attempts:3}") int maxRetryAttempts,
            @Value("${hmrc.callback.initial-delay-millis:1000}") int initialDelayMillis,
            @Value("${hmrc.callback.backoff-multiplier:2.0}") double backoffMultiplier,
            @Value("${hmrc.callback.executor-thread-pool-size:10}") int executorThreadPoolSize) {
        this.callbackClient = callbackClient;
        this.callbackEndpointUrl = callbackEndpointUrl;
        this.maxRetryAttempts = maxRetryAttempts;
        this.initialDelayMillis = initialDelayMillis;
        this.backoffMultiplier = backoffMultiplier;
        this.executorService = new AtomicReference<>(Executors.newScheduledThreadPool(executorThreadPoolSize));

        LOGGER.debug(format("HmrcCallbackService initialised: endpoint=%s, maxAttempts=%d, initialDelay=%dms, backoff=%.1f, executorThreads=%d",
                callbackEndpointUrl.isEmpty() ? "<not-configured>" : callbackEndpointUrl,
                maxRetryAttempts, initialDelayMillis, backoffMultiplier, executorThreadPoolSize));
    }

    /**
     * Gracefully shuts down the executor service.
     * Invoked by Spring when the application context is closing.
     */
    @Override
    public void destroy() {
        synchronized (executorLock) {
            if (isShuttingDown.get() || executorService.get() == null) {
                return;
            }
            isShuttingDown.set(true);
            executorService.get().shutdown();
        }

        try {
            ScheduledExecutorService executor = executorService.get();
            if (executor != null && !executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.error("Executor service did not terminate within timeout, forcing shutdown");
                executor.shutdownNow();
            }
            LOGGER.info("HmrcCallbackService executor service shut down gracefully");
        } catch (InterruptedException ex) {
            LOGGER.error("Interrupted while waiting for executor service shutdown", ex);
            ScheduledExecutorService executor = executorService.get();
            if (executor != null) {
                executor.shutdownNow();
            }
            Thread.currentThread().interrupt();
        }
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
     * failure and invokes the result handler if provided. Thread-safe with protection
     * against executor shutdown.</p>
     *
     * @param payload the callback payload to send
     * @param attemptNumber the current attempt number (0-based)
     * @param resultHandler optional handler to invoke with callback result
     */
     private void submitCallbackWithRetry(HmrcCallbackPayload payload, int attemptNumber,
                                         BiConsumer<String, String> resultHandler) {
         synchronized (executorLock) {
             if (!isExecutorAvailable()) {
                 notifyShutdown(payload, resultHandler);
                 return;
             }

            try {
                executeCallback(payload, attemptNumber, resultHandler);
            } catch (RejectedExecutionException ex) {
                handleCallbackExecutionRejection(payload, attemptNumber, ex, resultHandler);
            }
        }
     }

    /**
     * Executes the callback task asynchronously.
     *
     * @param payload the callback payload to send
     * @param attemptNumber the current attempt number (0-based)
     * @param resultHandler optional handler to invoke with callback result
     */
    private void executeCallback(HmrcCallbackPayload payload, int attemptNumber, BiConsumer<String, String> resultHandler) {
        executorService.get().execute(() -> {
            try {
                LOGGER.debug(format("Attempting HMRC callback: resourceId=%s, attempt=%d",
                        payload.getResourceId(), attemptNumber + 1));

                String correlationId = callbackClient.sendCallback(callbackEndpointUrl, payload);
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
     * Handles rejection when submitting callback task to executor.
     *
     * @param payload the callback payload that failed to submit
     * @param attemptNumber the current attempt number (0-based)
     * @param ex the rejection exception
     * @param resultHandler optional handler to invoke with failure reason
     */
    private void handleCallbackExecutionRejection(HmrcCallbackPayload payload, int attemptNumber,
                                                   RejectedExecutionException ex, BiConsumer<String, String> resultHandler) {
        LOGGER.error(format("Failed to submit HMRC callback task (executor rejected): resourceId=%s, attempt=%d",
                payload.getResourceId(), attemptNumber + 1), ex);
        if (resultHandler != null) {
            String failureMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            resultHandler.accept(null, failureMessage);
        }
    }

    /**
     * Checks if executor is available for task submission.
     *
     * @return true if executor is available, false otherwise
     */
    private boolean isExecutorAvailable() {
        return !isShuttingDown.get() && executorService.get() != null && !executorService.get().isShutdown();
    }

    /**
     * Notifies handler that service is shutting down.
     *
     * @param payload the callback payload being rejected
     * @param resultHandler optional handler to invoke with shutdown message
     */
    private void notifyShutdown(HmrcCallbackPayload payload, BiConsumer<String, String> resultHandler) {
        LOGGER.error(format("Cannot submit callback task: resourceId=%s, isShuttingDown=%s",
                payload.getResourceId(), isShuttingDown.get()));
        if (resultHandler != null) {
            resultHandler.accept(null, SHUTDOWN_MESSAGE);
        }
    }

     /**
      * Handles a failed callback by logging and potentially scheduling a retry.
     *
     * <p>Logs the failure with traceability information. If the maximum retry
     * attempts have not been exceeded, schedules a retry with exponential backoff.
     * Otherwise, logs the final failure and invokes the result handler if provided.
     * Thread-safe with protection against executor shutdown.</p>
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

        if (shouldRetry(attemptNumber)) {
            scheduleRetry(payload, attemptNumber, resultHandler);
        } else {
            handleRetriesExhausted(payload, ex, resultHandler);
        }
     }

    /**
     * Determines whether another retry should be attempted.
     *
     * @param attemptNumber the current attempt number (0-based)
     * @return true if retries remain, false otherwise
     */
    private boolean shouldRetry(int attemptNumber) {
        return attemptNumber < maxRetryAttempts - 1;
    }

    /**
     * Schedules a retry of the callback with exponential backoff.
     *
     * @param payload the callback payload to retry
     * @param attemptNumber the current attempt number (0-based)
     * @param resultHandler optional handler to invoke with result
     */
    private void scheduleRetry(HmrcCallbackPayload payload, int attemptNumber, BiConsumer<String, String> resultHandler) {
        long delayMillis = calculateDelay(attemptNumber);
        LOGGER.info(format("Scheduling HMRC callback retry: resourceId=%s, nextAttempt=%d, delayMillis=%d",
                payload.getResourceId(), attemptNumber + 2, delayMillis));

        synchronized (executorLock) {
            if (!isExecutorAvailable()) {
                notifyShutdown(payload, resultHandler);
                return;
            }

            try {
                executorService.get().schedule(
                        () -> submitCallbackWithRetry(payload, attemptNumber + 1, resultHandler),
                        delayMillis,
                        TimeUnit.MILLISECONDS
                );
            } catch (RejectedExecutionException rejEx) {
                handleRetrySchedulingRejection(payload, attemptNumber, rejEx, resultHandler);
            }
        }
    }

    /**
     * Handles rejection when scheduling a callback retry.
     *
     * @param payload the callback payload that failed to schedule
     * @param attemptNumber the current attempt number (0-based)
     * @param ex the rejection exception
     * @param resultHandler optional handler to invoke with failure reason
     */
    private void handleRetrySchedulingRejection(HmrcCallbackPayload payload, int attemptNumber,
                                                 RejectedExecutionException ex, BiConsumer<String, String> resultHandler) {
        LOGGER.error(format("Failed to schedule HMRC callback retry (executor rejected): resourceId=%s, nextAttempt=%d",
                payload.getResourceId(), attemptNumber + 2), ex);
        if (resultHandler != null) {
            String failureMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            resultHandler.accept(null, failureMessage);
        }
    }

    /**
     * Handles the case when all retry attempts have been exhausted.
     *
     * @param payload the callback payload that failed
     * @param ex the exception that caused the final failure
     * @param resultHandler optional handler to invoke with failure reason
     */
    private void handleRetriesExhausted(HmrcCallbackPayload payload, Exception ex, BiConsumer<String, String> resultHandler) {
        LOGGER.error(format("HMRC callback exhausted all retry attempts: resourceId=%s, companyNumber=%s, totalAttempts=%d",
                payload.getResourceId(), payload.getCompanyNumber(), maxRetryAttempts));

        if (resultHandler != null) {
            String failureMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            resultHandler.accept(null, failureMessage);
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
