package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

public class KafkaPublishException extends RuntimeException {
    public KafkaPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
