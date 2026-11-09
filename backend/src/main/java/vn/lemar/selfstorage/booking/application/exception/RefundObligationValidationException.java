package vn.lemar.selfstorage.booking.application.exception;

/** Dữ liệu tạo refund obligation không hợp lệ ở tầng booking (invoice/order/facility). */
public class RefundObligationValidationException extends RuntimeException {

    public RefundObligationValidationException(String message) {
        super(message);
    }
}
