package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

import java.io.Serial;

/**
 * The class {@code CompanyValidationException} is a form of {@code RuntimeException}
 * that is thrown when company validation fails (e.g., company not found, name mismatch, invalid status).
 * It includes an error code that maps to contract-defined API error responses.
 */
public class CompanyValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4756738290476930286L;

    private final String errorCode;

    /**
     * Constructs a new {@code CompanyValidationException} with a custom message and error code.
     *
     * @param message a custom message
     * @param errorCode the error code (e.g., COMPANY_NUMBER_NOT_EXIST)
     */
    public CompanyValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
