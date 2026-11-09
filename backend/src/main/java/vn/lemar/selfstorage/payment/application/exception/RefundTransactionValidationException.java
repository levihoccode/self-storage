package vn.lemar.selfstorage.payment.application.exception;

/** Dữ liệu hoàn tiền không hợp lệ (số tiền, nguồn, lý do, chứng từ). */
public class RefundTransactionValidationException extends RuntimeException {

    public RefundTransactionValidationException(String message) {
        super(message);
    }
}
