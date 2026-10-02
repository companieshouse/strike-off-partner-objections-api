package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

/**
 * Enumeration of callback processing statuses.
 *
 * <p>Tracks the outcome of HMRC callback notification attempts.</p>
 */
public enum CallbackStatus {
    /**
     * Callback notification was sent successfully.
     */
    SUCCESS,

    /**
     * Callback notification failed after all retry attempts.
     */
    FAILED
}
