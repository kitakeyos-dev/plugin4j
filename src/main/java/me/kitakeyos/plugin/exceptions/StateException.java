package me.kitakeyos.plugin.exceptions;

/**
 * Exception thrown when plugin state operations fail
 * Used for state capture, restoration, and persistence errors
 */
public class StateException extends RuntimeException {

    /**
     * Creates a state exception with message
     *
     * @param message Descriptive error message
     */
    public StateException(String message) {
        super(message);
    }

    /**
     * Creates a state exception with message and cause
     *
     * @param message Descriptive error message
     * @param cause   Underlying exception that caused this error
     */
    public StateException(String message, Throwable cause) {
        super(message, cause);
    }
}