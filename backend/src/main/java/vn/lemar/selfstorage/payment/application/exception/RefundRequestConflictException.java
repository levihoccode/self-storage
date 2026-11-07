package vn.lemar.selfstorage.payment.application.exception;

/** Đã có refund request active cho invoice/order, hoặc request không còn nhận thao tác. */
public class RefundRequestConflictException extends RuntimeException {

    public RefundRequestConflictException(String message) {
        super(message);
    }
}
