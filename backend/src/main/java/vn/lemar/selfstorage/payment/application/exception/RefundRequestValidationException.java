package vn.lemar.selfstorage.payment.application.exception;

/** Dữ liệu tạo refund request không hợp lệ (lines, reason, liên kết invoice/order). */
public class RefundRequestValidationException extends RuntimeException {

    public RefundRequestValidationException(String message) {
        super(message);
    }
}
