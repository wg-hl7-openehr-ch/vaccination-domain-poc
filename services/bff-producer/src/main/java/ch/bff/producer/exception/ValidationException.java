package ch.bff.producer.exception;

/**
 * Business validation failure whose message is safe to show to the user.
 * Mapped to HTTP 400 by {@link RestExceptionHandler}.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
