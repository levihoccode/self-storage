package vn.lemar.selfstorage.identity.application.exception;

public class EmailAlreadyVerifiedException extends RuntimeException {
    public EmailAlreadyVerifiedException() {
        super("Tài khoản đã được xác minh");
    }
}
