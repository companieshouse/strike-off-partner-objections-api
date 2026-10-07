package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.beans.factory.DisposableBean;
import uk.gov.companieshouse.logging.util.DataMap;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackResourceKind;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.HmrcCallbackPayload;

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

        var logMap = new DataMap.Builder()
                .topic("hmrc-callback")
                .retryCount(maxRetryAttempts)
                .durationMs((long) initialDelayMillis)
                .build()
                .getLogMap();
        LOGGER.debug("HmrcCallbackService initialised with configuration", logMap);
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
                var logMap = new DataMap.Builder()
                        .errorMessage("Executor service shutdown timeout exceeded")
                        .build()
                        .getLogMap();
                LOGGER.error("Executor service forced shutdown", logMap);
                executor.shutdownNow();
            }
            var logMap = new DataMap.Builder().build().getLogMap();
            LOGGER.info("HmrcCallbackService executor shutdown complete", logMap);
        } catch (InterruptedException ex) {
            var logMap = new DataMap.Builder()
                    .errorMessage("Interrupted during executor service shutdown")
                    .build()
                    .getLogMap();
            LOGGER.error("Executor service shutdown interrupted", ex, logMap);
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
     * @param resultHandler optional handler to be invoked with callback outcome containing correlationId, attemptNumber, and failureReason
     */
    public void sendObjectionOutcomeCallback(String objectionId, String companyNumber, String objectionsUri,
                                            Consumer<CallbackResult> resultHandler) {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.OBJECTION,
                objectionId,
                companyNumber,
                objectionsUri
        );
        submitCallbackWithRetry(payload, 1, resultHandler);
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
     * @param resultHandler optional handler to be invoked with callback outcome containing correlationId, attemptNumber, and failureReason
     */
    public void sendWithdrawalOutcomeCallback(String withdrawalId, String companyNumber, String withdrawalUri,
                                            Consumer<CallbackResult> resultHandler) {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.WITHDRAWAL,
                withdrawalId,
                companyNumber,
                withdrawalUri
        );
        submitCallbackWithRetry(payload, 1, resultHandler);
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
     * @param attemptNumber the current attempt number (1-based)
     * @param resultHandler optional handler to invoke with callback result
     */
     private void submitCallbackWithRetry(HmrcCallbackPayload payload, int attemptNumber,
                                         Consumer<CallbackResult> resultHandler) {
         synchronized (executorLock) {
             if (isExecutorUnavailable()) {
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
     * @param attemptNumber the current attempt number (1-based)
     * @param resultHandler optional handler to invoke with callback result
     */
    private void executeCallback(HmrcCallbackPayload payload, int attemptNumber, Consumer<CallbackResult> resultHandler) {
        executorService.get().execute(() -> {
            try {
                var logMap = new DataMap.Builder()
                        .resourceId(payload.getResourceId())
                        .resourceKind(payload.getResourceKind().name())
                        .retryCount(attemptNumber)
                        .build()
                        .getLogMap();
                LOGGER.debugContext(null, "Attempting HMRC callback", logMap);

                String correlationId = callbackClient.sendCallback(callbackEndpointUrl, payload);
                if (resultHandler != null) {
                    resultHandler.accept(new CallbackResult(correlationId, attemptNumber));
                }
            } catch (RestClientException ex) {
                handleCallbackFailure(payload, attemptNumber, ex, resultHandler);
            } catch (Exception ex) {
                var logMap = new DataMap.Builder()
                        .resourceId(payload.getResourceId())
                        .resourceKind(payload.getResourceKind().name())
                        .retryCount(attemptNumber)
                        .errorMessage("Unexpected error during callback execution")
                        .build()
                        .getLogMap();
                LOGGER.error("Unexpected error during HMRC callback", ex, logMap);
                handleCallbackFailure(payload, attemptNumber, ex, resultHandler);
            }
        });
    }

    /**
     * Handles rejection when submitting callback task to executor.
     *
     * @param payload the callback payload that failed to submit
     * @param attemptNumber the current attempt number (1-based)
     * @param ex the rejection exception
     * @param resultHandler optional handler to invoke with failure reason
     */
    private void handleCallbackExecutionRejection(HmrcCallbackPayload payload, int attemptNumber,
                                                   RejectedExecutionException ex, Consumer<CallbackResult> resultHandler) {
        var logMap = new DataMap.Builder()
                .resourceId(payload.getResourceId())
                .resourceKind(payload.getResourceKind().name())
                .retryCount(attemptNumber)
                .errorMessage("Executor rejected callback task submission")
                .build()
                .getLogMap();
        LOGGER.error("Failed to submit HMRC callback task", ex, logMap);
        if (resultHandler != null) {
            String failureMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            resultHandler.accept(new CallbackResult(attemptNumber, failureMessage));
        }
    }

    /**
     * Checks if executor is unavailable for task submission.
     *
     * @return true if executor is unavailable, false otherwise
     */
    private boolean isExecutorUnavailable() {
        return isShuttingDown.get() || executorService.get() == null || executorService.get().isShutdown();
    }

    /**
     * Notifies handler that service is shutting down.
     *
     * @param payload the callback payload being rejected
     * @param resultHandler optional handler to invoke with shutdown message
     */
    private void notifyShutdown(HmrcCallbackPayload payload, Consumer<CallbackResult> resultHandler) {
        var logMap = new DataMap.Builder()
                .resourceId(payload.getResourceId())
                .resourceKind(payload.getResourceKind().name())
                .errorMessage("Service is shutting down, cannot submit callback task")
                .build()
                .getLogMap();
        LOGGER.error("Cannot submit callback task: service shutting down", logMap);
        if (resultHandler != null) {
            resultHandler.accept(new CallbackResult(1, SHUTDOWN_MESSAGE));
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
     * @param attemptNumber the current attempt number (1-based)
     * @param ex the exception that caused the failure
     * @param resultHandler optional handler to invoke when retries exhausted
     */
    private void handleCallbackFailure(HmrcCallbackPayload payload, int attemptNumber, Exception ex,
                                      Consumer<CallbackResult> resultHandler) {
        var logMap = new DataMap.Builder()
                .resourceId(payload.getResourceId())
                .companyNumber(payload.getCompanyNumber())
                .resourceKind(payload.getResourceKind().name())
                .retryCount(attemptNumber)
                .errorMessage(ex.getMessage())
                .build()
                .getLogMap();
        LOGGER.error("HMRC callback failed", ex, logMap);

        if (shouldRetry(attemptNumber)) {
            scheduleRetry(payload, attemptNumber, resultHandler);
        } else {
            handleRetriesExhausted(payload, attemptNumber, ex, resultHandler);
        }
     }

    /**
     * Determines whether another retry should be attempted.
     *
     * @param attemptNumber the current attempt number (1-based)
     * @return true if retries remain, false otherwise
     */
    private boolean shouldRetry(int attemptNumber) {
        return attemptNumber < maxRetryAttempts;
    }

    /**
     * Schedules a retry of the callback with exponential backoff.
     *
     * @param payload the callback payload to retry
     * @param attemptNumber the current attempt number (1-based)
     * @param resultHandler optional handler to invoke with result
     */
    private void scheduleRetry(HmrcCallbackPayload payload, int attemptNumber, Consumer<CallbackResult> resultHandler) {
        long delayMillis = calculateDelay(attemptNumber);
        var logMap = new DataMap.Builder()
                .resourceId(payload.getResourceId())
                .resourceKind(payload.getResourceKind().name())
                .retryCount(attemptNumber + 1)
                .durationMs(delayMillis)
                .build()
                .getLogMap();
        LOGGER.info("Scheduling HMRC callback retry", logMap);

        synchronized (executorLock) {
            if (isExecutorUnavailable()) {
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
     * @param attemptNumber the current attempt number (1-based)
     * @param ex the rejection exception
     * @param resultHandler optional handler to invoke with failure reason
     */
    private void handleRetrySchedulingRejection(HmrcCallbackPayload payload, int attemptNumber,
                                                 RejectedExecutionException ex, Consumer<CallbackResult> resultHandler) {
        var logMap = new DataMap.Builder()
                .resourceId(payload.getResourceId())
                .resourceKind(payload.getResourceKind().name())
                .retryCount(attemptNumber + 1)
                .errorMessage("Executor rejected retry scheduling")
                .build()
                .getLogMap();
        LOGGER.error("Failed to schedule HMRC callback retry", ex, logMap);
        if (resultHandler != null) {
            String failureMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            resultHandler.accept(new CallbackResult(attemptNumber + 1, failureMessage));
        }
    }

    /**
     * Handles the case when all retry attempts have been exhausted.
     *
     * @param payload the callback payload that failed
     * @param attemptNumber the final attempt number (1-based)
     * @param ex the exception that caused the final failure
     * @param resultHandler optional handler to invoke with failure reason
     */
    private void handleRetriesExhausted(HmrcCallbackPayload payload, int attemptNumber, Exception ex, Consumer<CallbackResult> resultHandler) {
        var logMap = new DataMap.Builder()
                .resourceId(payload.getResourceId())
                .companyNumber(payload.getCompanyNumber())
                .resourceKind(payload.getResourceKind().name())
                .retryCount(attemptNumber)
                .errorMessage("All callback retry attempts exhausted")
                .build()
                .getLogMap();
        LOGGER.error("HMRC callback exhausted all retry attempts", logMap);

        if (resultHandler != null) {
            String failureMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            resultHandler.accept(new CallbackResult(attemptNumber, failureMessage));
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
