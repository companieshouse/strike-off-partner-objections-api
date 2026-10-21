package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of resource types supported by HMRC callback notifications.
 */
public enum CallbackResourceKind {
    /**
     * Represents an objection resource.
     */
    OBJECTION("strike-off-partner-objection#objection"),

    /**
     * Represents a withdrawal resource.
     */
    WITHDRAWAL("strike-off-partner-objection#withdrawal");

    private final String value;

    CallbackResourceKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}

