package co.wethinkcode.healthsafe;

public class DownstreamNotFoundException extends RuntimeException {

    public DownstreamNotFoundException(String message) {
        super(message);
    }
}
