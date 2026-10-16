package vn.lemar.selfstorage.identity.application.exception;

public class TooManyVerificationAttemptsException extends RuntimeException {
    public TooManyVerificationAttemptsException() {
        super("Bạn đã nhập sai quá nhiều lần. Vui lòng gửi lại mã mới.");
    }
}
