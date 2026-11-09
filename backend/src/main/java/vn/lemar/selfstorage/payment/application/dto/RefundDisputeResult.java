package vn.lemar.selfstorage.payment.application.dto;

/** Kết quả dispute; {@code newlyDisputed=false} nghĩa là lần gọi lặp, không phát notification/audit. */
public record RefundDisputeResult(RefundTransactionView refund, boolean newlyDisputed) {
}
