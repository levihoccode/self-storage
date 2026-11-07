package vn.lemar.selfstorage.payment.application.exception;

/** Trạng thái hiện tại không cho phép ghi nhận/xác nhận hoàn tiền. */
public class RefundTransactionConflictException extends RuntimeException {

    public RefundTransactionConflictException(String message) {
        super(message);
    }
}
