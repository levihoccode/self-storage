package vn.lemar.selfstorage.booking.application.exception;

/** Actor không phải ADMIN/FM nên không được xem queue hoàn tiền. */
public class RefundQueueForbiddenException extends RuntimeException {

    public RefundQueueForbiddenException() {
        super("Bạn không có quyền xem queue hoàn tiền");
    }
}
