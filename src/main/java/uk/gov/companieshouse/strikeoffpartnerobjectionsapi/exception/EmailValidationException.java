package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

import java.io.Serial;

/**
 * The class {@code EmailValidationException} is a form of {@code RuntimeException}
 * that is thrown when email validation fails (e.g., email is null, blank, exceeds maximum length, has an invalid format, or is not from a recognised source).
 * It includes an error code that maps to contract-defined API error responses.
 */
public class EmailValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4756738290476930286L;

    private final String errorCode;

    /**
     * Constructs a new {@code EmailValidationException} with a custom message and error code.
     *
     * @param message a custom message
     * @param errorCode the error code (e.g., EMAIL_INVALID_FORMAT)
     */
    public EmailValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
