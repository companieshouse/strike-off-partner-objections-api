package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

/**
 * Enumeration of resource types supported by HMRC callback notifications.
 */
public enum CallbackResourceKind {
    /**
     * Represents an objection resource.
     */
    OBJECTION,

    /**
     * Represents a withdrawal resource.
     */
    WITHDRAWAL
}
