package vn.lemar.selfstorage.identity.application.exception;

public class InvalidVerificationCodeException extends RuntimeException {
    public InvalidVerificationCodeException() {
        super("Mã xác minh không đúng hoặc đã hết hạn");
    }
}
