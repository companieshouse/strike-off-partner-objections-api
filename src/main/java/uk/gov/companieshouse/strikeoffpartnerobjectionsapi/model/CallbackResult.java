package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

/**
 * Immutable result of a callback operation containing correlation ID, attempt metadata, and failure reason.
 *
 * <p>Captures the complete outcome of a callback including which attempt succeeded (or all failed),
 * allowing handlers to make informed decisions about audit trail recording and retry policies.</p>
 */
public record CallbackResult(String correlationId, String failureReason) {

    public static CallbackResult success(String correlationId) {
        return new CallbackResult(correlationId, null);
    }

    public static CallbackResult failure(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("Failure reason must not be null or blank");
        }

        return new CallbackResult(null, failureReason);
    }

    public boolean isSuccess() {
        return failureReason == null;
    }

    @Override
    public String toString() {
        return "CallbackResult{" +
                "correlationId='" + correlationId + '\'' +
                ", failureReason='" + failureReason + '\'' +
                ", success=" + isSuccess() +
                '}';
    }
}

