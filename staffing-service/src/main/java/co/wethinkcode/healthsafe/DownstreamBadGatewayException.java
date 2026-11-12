package co.wethinkcode.healthsafe;

public class DownstreamBadGatewayException extends RuntimeException {

    public DownstreamBadGatewayException(String message) {
        super(message);
    }

    public DownstreamBadGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
