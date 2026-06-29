package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

public class KafkaPublishException extends RuntimeException {
    private final String eventId;

    public KafkaPublishException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public KafkaPublishException(String message, String eventId, Throwable cause) {
        super(message, cause);
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }
}
