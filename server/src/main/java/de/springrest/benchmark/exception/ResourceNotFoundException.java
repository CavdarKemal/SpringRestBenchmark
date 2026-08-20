package de.springrest.benchmark.exception;

/**
 * Wird geworfen, wenn eine angeforderte Ressource (z. B. eine Messung mit bestimmter id)
 * nicht existiert. Der {@link GlobalExceptionHandler} uebersetzt sie in HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
