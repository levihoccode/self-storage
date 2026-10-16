package vn.lemar.selfstorage.identity.application.exception;

public class EmailNotRegisteredException extends RuntimeException {
    public EmailNotRegisteredException() {
        super("Email chưa được đăng ký");
    }
}
