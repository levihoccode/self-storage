package vn.lemar.selfstorage.payment.application.exception;

/** Dữ liệu tạo refund obligation không hợp lệ (lines, reason, liên kết invoice/order). */
public class RefundObligationValidationException extends RuntimeException {

    public RefundObligationValidationException(String message) {
        super(message);
    }
}
