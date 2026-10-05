package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

/**
 * Immutable result of a callback operation containing correlation ID, attempt metadata, and failure reason.
 *
 * <p>Captures the complete outcome of a callback including which attempt succeeded (or all failed),
 * allowing handlers to make informed decisions about audit trail recording and retry policies.</p>
 */
public final class CallbackResult {
    private final String correlationId;
    private final int attemptNumber;
    private final String failureReason;

    /**
     * Constructs a CallbackResult for a successful callback.
     *
     * @param correlationId the HMRC correlation ID from the successful response
     * @param attemptNumber the attempt number that succeeded (1-based)
     */
    public CallbackResult(String correlationId, int attemptNumber) {
        this(correlationId, attemptNumber, null);
    }

    /**
     * Constructs a CallbackResult for a failed callback.
     *
     * @param attemptNumber the attempt number that failed (1-based)
     * @param failureReason the reason for the failure
     */
    public CallbackResult(int attemptNumber, String failureReason) {
        this(null, attemptNumber, failureReason);
    }

    /**
     * Constructs a CallbackResult with all fields.
     *
     * @param correlationId the HMRC correlation ID (null if failed)
     * @param attemptNumber the attempt number (1-based)
     * @param failureReason the failure reason (null if successful)
     */
    private CallbackResult(String correlationId, int attemptNumber, String failureReason) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        this.correlationId = correlationId;
        this.attemptNumber = attemptNumber;
        this.failureReason = failureReason;
    }

    /**
     * Returns the HMRC correlation ID from a successful callback, or null if failed.
     *
     * @return correlation ID or null
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Returns the attempt number (1-based) on which this result occurred.
     *
     * @return attempt number
     */
    public int getAttemptNumber() {
        return attemptNumber;
    }

    /**
     * Returns the failure reason if the callback failed, or null if successful.
     *
     * @return failure reason or null
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * Returns whether the callback succeeded.
     *
     * @return true if failureReason is null, false otherwise
     */
    public boolean isSuccess() {
        return failureReason == null;
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return "CallbackResult{" +
                    "correlationId='" + correlationId + '\'' +
                    ", attemptNumber=" + attemptNumber +
                    ", success=true" +
                    '}';
        } else {
            return "CallbackResult{" +
                    "attemptNumber=" + attemptNumber +
                    ", failureReason='" + failureReason + '\'' +
                    ", success=false" +
                    '}';
        }
    }
}

