package vn.lemar.selfstorage.identity.application.exception;

public class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException() {
        super("Yeu cau chua duoc xac thuc");
    }
}