package vn.lemar.selfstorage.payment.application.exception;

/** Không tìm thấy giao dịch hoàn tiền, hoặc giao dịch không thuộc hoá đơn đang xử lý. */
public class RefundTransactionNotFoundException extends RuntimeException {

    public RefundTransactionNotFoundException() {
        super("Không tìm thấy giao dịch hoàn tiền");
    }
}
