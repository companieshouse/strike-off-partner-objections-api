package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

public class CompanyProfileServiceException extends RuntimeException {

    public CompanyProfileServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public CompanyProfileServiceException(String message) {
        super(message);
    }
}

