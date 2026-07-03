package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

/**
 * The class {@code ServiceException} is a form of {@code Exception}
 * that should be used at the service layer to abstract lower level
 * exceptions from being propagated up the call stack.
 */
public class ServiceException extends RuntimeException {

    /**
     *
     */
    private static final long serialVersionUID = 8335305656549044901L;

    /**
     * Constructs a new {@code ServiceException} with a custom message and the specified
     * cause.
     *
     * @param message a custom message
     * @param cause the cause
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}