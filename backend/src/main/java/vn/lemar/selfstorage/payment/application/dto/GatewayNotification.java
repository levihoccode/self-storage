package vn.lemar.selfstorage.payment.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

/**
 * Kết quả một callback/IPN đã được adapter của cổng xác thực và chuẩn hoá.
 *
 * <p>Service chỉ làm việc với dạng này, không biết field/thuật toán của từng cổng.
 */
public record GatewayNotification(String txnRef, PaymentStatus status, BigDecimal amount,
                                  Instant paidAt, String gatewayTransactionNo, String rawPayload) {
}
