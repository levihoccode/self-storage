package vn.lemar.selfstorage.identity.application.exception;

public class TooManyLoginAttemptsException extends RuntimeException {
    public TooManyLoginAttemptsException() {
        super("Quá nhiều lần thử. Vui lòng thử lại sau.");
    }
}
