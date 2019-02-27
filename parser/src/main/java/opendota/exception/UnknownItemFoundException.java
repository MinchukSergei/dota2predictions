package opendota.exception;

public class UnknownItemFoundException extends RuntimeException {
    public UnknownItemFoundException(String message) {
        super(message);
    }
}