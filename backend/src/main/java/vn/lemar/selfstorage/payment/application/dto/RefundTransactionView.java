package vn.lemar.selfstorage.payment.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.lemar.selfstorage.payment.domain.RefundTransaction;
import vn.lemar.selfstorage.payment.domain.RefundTransactionStatus;

/** Kết quả một lần ghi nhận/hoàn tất hoàn tiền. */
public record RefundTransactionView(
        Long id,
        Long invoiceId,
        Long sourceTransactionId,
        BigDecimal amount,
        String method,
        String status,
        String reasonCode,
        String reasonNote,
        String gatewayRef,
        String evidenceUrl,
        String note,
        Long handledByAccountId,
        Instant disbursedAt,
        Instant customerConfirmedAt,
        Instant disputedAt,
        String disputeNote,
        Instant createdAt,
        Instant updatedAt) {

    public static RefundTransactionView from(RefundTransaction refund) {
        return new RefundTransactionView(
                refund.getId(),
                refund.getInvoiceId(),
                refund.getSourceTransactionId(),
                refund.getAmount(),
                refund.getMethod() == null ? null : refund.getMethod().name(),
                refund.getStatus() == null ? null : refund.getStatus().dbValue(),
                refund.getReasonCode().name(),
                refund.getReasonNote(),
                refund.getGatewayRef(),
                refund.getEvidenceUrl(),
                refund.getNote(),
                refund.getHandledByAccountId(),
                refund.getDisbursedAt(),
                refund.getCustomerConfirmedAt(),
                refund.getDisputedAt(),
                refund.getDisputeNote(),
                refund.getCreatedAt(),
                refund.getUpdatedAt());
    }

    public boolean isSuccess() {
        return RefundTransactionStatus.SUCCESS.dbValue().equals(status);
    }
}
