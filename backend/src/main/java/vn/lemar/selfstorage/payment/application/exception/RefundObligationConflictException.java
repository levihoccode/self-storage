package vn.lemar.selfstorage.payment.application.exception;

/** Đã có refund obligation active cho invoice/order, hoặc request không còn nhận thao tác. */
public class RefundObligationConflictException extends RuntimeException {

    public RefundObligationConflictException(String message) {
        super(message);
    }
}
