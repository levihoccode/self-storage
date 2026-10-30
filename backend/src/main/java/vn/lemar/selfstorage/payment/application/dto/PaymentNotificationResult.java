package vn.lemar.selfstorage.payment.application.dto;

/** Kết quả xử lý một callback thanh toán, độc lập với định dạng phản hồi của từng cổng. */
public enum PaymentNotificationResult {
    ACCEPTED,
    ALREADY_SETTLED,
    NOT_FOUND,
    INVALID_AMOUNT,
    IGNORED
}
