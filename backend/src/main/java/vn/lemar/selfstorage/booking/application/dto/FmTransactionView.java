package vn.lemar.selfstorage.booking.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một dòng lịch sử dòng tiền của cơ sở: invoice/fee (chứng từ), payment (tiền vào) hoặc refund
 * (tiền ra). {@code id} là id của bản ghi nguồn; {@code kind} cho biết đọc từ bảng nào.
 */
public record FmTransactionView(
        Long id,
        String kind,
        String direction,
        BigDecimal amount,
        String status,
        Instant occurredAt,
        Long orderId,
        Long invoiceId,
        String invoiceCode,
        Long paymentTransactionId,
        Long refundTransactionId,
        String code,
        String method,
        String description,
        Long customerId,
        String customerName,
        String customerEmail,
        Long facilityId,
        String facilityName,
        String unitCode,
        String invoiceRefundStatus,
        String invoiceRefundReason,
        long pendingAgeMinutes,
        boolean needsReconciliation,
        Instant refundConfirmedAt,
        Instant refundDisputedAt,
        String refundDisputeNote) {
}
