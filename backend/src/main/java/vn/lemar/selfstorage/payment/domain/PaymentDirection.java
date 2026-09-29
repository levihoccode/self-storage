package vn.lemar.selfstorage.payment.domain;

/**
 * Chiều giao dịch. Giá trị trong DB đã viết HOA sẵn nên map thẳng được, không cần converter.
 *
 * <p>{@code REFUND} chưa dùng trong MVP — hoàn tiền do FM xử lý thủ công rồi ghi nhận lại.
 */
public enum PaymentDirection {
    PAY,
    REFUND
}
