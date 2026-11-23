package ch.bff.producer.exception;

/**
 * A requested entry does not exist (for this person). The message is safe to
 * show to the user. Mapped to HTTP 404 by {@link RestExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
