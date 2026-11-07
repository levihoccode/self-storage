package vn.lemar.selfstorage.booking.application.exception;

/** Dữ liệu tạo refund request không hợp lệ ở tầng booking (invoice/order/facility). */
public class RefundRequestValidationException extends RuntimeException {

    public RefundRequestValidationException(String message) {
        super(message);
    }
}
