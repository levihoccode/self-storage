package vn.lemar.selfstorage.booking.application.exception;

/** Hoá đơn/trạng thái hiện tại không cho phép thao tác hoàn tiền. */
public class RefundNotAllowedException extends RuntimeException {

    public RefundNotAllowedException(String message) {
        super(message);
    }
}
