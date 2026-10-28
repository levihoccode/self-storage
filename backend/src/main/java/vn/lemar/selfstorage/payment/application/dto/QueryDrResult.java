package vn.lemar.selfstorage.payment.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kết quả đối soát từ cổng. {@code responseCode} là kết quả của lời gọi API; trạng thái thật của
 * giao dịch nằm ở {@code transactionStatus}.
 */
public record QueryDrResult(String responseCode, String transactionStatus, String transactionNo,
                            Instant paidAt, BigDecimal amount, String message, String rawResponse) {
}
