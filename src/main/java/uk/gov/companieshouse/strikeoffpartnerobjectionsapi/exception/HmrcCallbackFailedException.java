package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

import java.io.Serial;

/**
 * Indicates that an HMRC callback failed after its outcome was persisted.
 */
public class HmrcCallbackFailedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public HmrcCallbackFailedException(String message) {
        super(message);
    }
}