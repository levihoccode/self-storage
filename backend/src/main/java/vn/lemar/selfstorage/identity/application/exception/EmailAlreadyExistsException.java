package vn.lemar.selfstorage.identity.application.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("Email đã được đăng ký");
    }
}