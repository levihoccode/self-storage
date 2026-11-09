package vn.lemar.selfstorage.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import vn.lemar.selfstorage.payment.application.RefundReason;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một lần chi tiền hoàn cho khách — ledger tiền ra, tách khỏi {@link PaymentTransaction}
 * (chỉ ghi tiền vào). {@code invoiceId}/{@code sourceTransactionId} là khoá mềm sang module
 * booking/payment, không dùng quan hệ JPA để giữ ranh giới module.
 */
@Entity
@Table(name = "refund_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "source_transaction_id", nullable = false)
    private Long sourceTransactionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundMethod method;

    @Convert(converter = RefundTransactionStatusConverter.class)
    @Column(nullable = false, length = 20)
    private RefundTransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30)
    private RefundReason reasonCode;

    @Column(name = "reason_note")
    private String reasonNote;

    @Column(name = "gateway_ref", length = 100)
    private String gatewayRef;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column
    private String note;

    @Column(name = "handled_by_account_id")
    private Long handledByAccountId;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "customer_confirmed_at")
    private Instant customerConfirmedAt;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @Column(name = "dispute_note")
    private String disputeNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Tiền mặt/chuyển khoản do FS/FM xác nhận đã đưa: ghi thẳng {@code Success}. Refund qua cổng
     * sẽ tạo {@code Pending} ở luồng VNPay sau này.
     */
    public static RefundTransaction success(Long invoiceId, Long sourceTransactionId, BigDecimal amount,
            RefundMethod method, RefundReason reasonCode, String reasonNote, String gatewayRef,
            String evidenceUrl, String note, Long handledByAccountId, Instant disbursedAt) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Số tiền hoàn phải lớn hơn 0");
        }
        if (reasonCode == RefundReason.OTHER && (reasonNote == null || reasonNote.isBlank())) {
            throw new IllegalArgumentException("Lý do OTHER phải có ghi chú");
        }
        RefundTransaction refund = new RefundTransaction();
        refund.invoiceId = invoiceId;
        refund.sourceTransactionId = sourceTransactionId;
        refund.amount = amount;
        refund.method = method;
        refund.status = RefundTransactionStatus.SUCCESS;
        refund.reasonCode = reasonCode;
        refund.reasonNote = reasonNote;
        refund.gatewayRef = gatewayRef;
        refund.evidenceUrl = evidenceUrl;
        refund.note = note;
        refund.handledByAccountId = handledByAccountId;
        refund.disbursedAt = disbursedAt;
        return refund;
    }

    /** Khách báo chưa nhận; chỉ ghi lần đầu, lần lặp giữ nguyên mốc và note. */
    public boolean markDisputed(Instant disputedAt, String note) {
        if (this.disputedAt != null) {
            return false;
        }
        this.disputedAt = disputedAt;
        this.disputeNote = note;
        this.updatedAt = disputedAt;
        return true;
    }

    /** Khách xác nhận đã nhận tiền; gọi lặp lại không đổi mốc thời gian. */
    public boolean markCustomerConfirmed(Instant confirmedAt) {
        if (customerConfirmedAt != null) {
            return false;
        }
        this.customerConfirmedAt = confirmedAt;
        this.updatedAt = confirmedAt;
        return true;
    }
}
