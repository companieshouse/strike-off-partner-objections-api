 package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

public class CompanyValidationException extends RuntimeException {

    private final String errorCode;

    public CompanyValidationException(String errorCode) {
        super("Company validation failed: " + errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

