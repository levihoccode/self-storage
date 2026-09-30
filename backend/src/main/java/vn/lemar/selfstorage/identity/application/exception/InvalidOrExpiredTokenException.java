package vn.lemar.selfstorage.identity.application.exception;

public class InvalidOrExpiredTokenException extends RuntimeException {
    public InvalidOrExpiredTokenException() {
        super("Link xác thực không hợp lệ hoặc đã hết hạn");
    }
}