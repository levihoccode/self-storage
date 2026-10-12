package vn.lemar.selfstorage.identity.application.exception;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Bạn không có quyền thực hiện thao tác này");
    }

    public ForbiddenException(String message) {
        super(message);
    }
}