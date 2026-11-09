package vn.lemar.selfstorage.payment.application.exception;

/** Tham số view của refund queue không hợp lệ. */
public class RefundQueueFilterException extends RuntimeException {

    public RefundQueueFilterException(String message) {
        super(message);
    }
}
