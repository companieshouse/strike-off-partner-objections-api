package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

public class ObjectionNotFoundException extends RuntimeException {
    public ObjectionNotFoundException(String message) {
        super(message);
    }
}
