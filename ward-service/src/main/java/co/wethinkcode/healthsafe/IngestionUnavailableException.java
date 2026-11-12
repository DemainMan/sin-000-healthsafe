package co.wethinkcode.healthsafe;

public class IngestionUnavailableException extends RuntimeException {

    public IngestionUnavailableException(String message) {
        super(message);
    }

    public IngestionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
