package vn.lemar.selfstorage.booking.application.exception;

/** Filter lịch sử giao dịch không hợp lệ — trả 400 kèm thông điệp cụ thể. */
public class FmTransactionFilterException extends RuntimeException {

    public FmTransactionFilterException(String message) {
        super(message);
    }
}
