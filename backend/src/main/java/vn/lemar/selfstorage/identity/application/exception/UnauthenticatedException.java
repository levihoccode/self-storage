package vn.lemar.selfstorage.identity.application.exception;

public class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException() {
        super("Yêu cầu chưa được xác thực");
    }
}