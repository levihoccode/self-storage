package vn.lemar.selfstorage.payment.application;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.payment.application.dto.RefundDisputeResult;
import vn.lemar.selfstorage.payment.application.dto.RefundTransactionView;
import vn.lemar.selfstorage.payment.application.exception.RefundTransactionConflictException;
import vn.lemar.selfstorage.payment.application.exception.RefundTransactionNotFoundException;
import vn.lemar.selfstorage.payment.application.exception.RefundTransactionValidationException;
import vn.lemar.selfstorage.payment.domain.PaymentDirection;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;
import vn.lemar.selfstorage.payment.domain.RefundMethod;
import vn.lemar.selfstorage.payment.domain.RefundTransaction;
import vn.lemar.selfstorage.payment.domain.RefundTransactionStatus;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;
import vn.lemar.selfstorage.payment.repository.RefundTransactionRepository;

/**
 * Ledger tiền ra. Không cập nhật invoice — booking sở hữu state máy hoá đơn và gọi service này
 * trong cùng transaction để audit + ledger commit/rollback cùng nhau.
 */
@Service
public class RefundTransactionService {

    private final PaymentTransactionRepository paymentTransactions;
    private final RefundTransactionRepository refundTransactions;
    private final AuditRecorder auditRecorder;

    public RefundTransactionService(PaymentTransactionRepository paymentTransactions,
            RefundTransactionRepository refundTransactions, AuditRecorder auditRecorder) {
        this.paymentTransactions = paymentTransactions;
        this.refundTransactions = refundTransactions;
        this.auditRecorder = auditRecorder;
    }

    /**
     * FS/FM xác nhận đã đưa tiền. Số tiền do booking lấy từ invoice và phải ≤ giao dịch gốc;
     * không nhận amount từ client. {@code sourceTransactionId} null thì lấy giao dịch PAY gần nhất
     * có thể hoàn (Success hoặc Duplicate).
     */
    @Transactional
    public RefundTransactionView recordSuccess(Long invoiceId, Long sourceTransactionId,
            BigDecimal amount, String methodCode, RefundReason reasonCode, String reasonNote,
            String gatewayRef, String evidenceUrl, String note, Long handledByAccountId) {
        if (invoiceId == null || methodCode == null || methodCode.isBlank()) {
            throw new RefundTransactionValidationException("Thiếu hoá đơn hoặc phương thức hoàn tiền");
        }
        if (reasonCode == null) {
            throw new RefundTransactionValidationException("Lý do hoàn tiền không hợp lệ");
        }
        if (reasonCode == RefundReason.OTHER && (reasonNote == null || reasonNote.isBlank())) {
            throw new RefundTransactionValidationException("Lý do OTHER phải có ghi chú");
        }
        validateEvidenceUrl(evidenceUrl);

        PaymentTransaction source = findSource(invoiceId, sourceTransactionId);
        if (!invoiceId.equals(source.getInvoiceId())) {
            throw new RefundTransactionValidationException("Giao dịch gốc không thuộc hoá đơn này");
        }
        if (source.getDirection() != PaymentDirection.PAY
                || (source.getStatus() != PaymentStatus.SUCCESS
                && source.getStatus() != PaymentStatus.DUPLICATE)) {
            throw new RefundTransactionValidationException(
                    "Giao dịch gốc phải là PAY Success hoặc Duplicate");
        }
        if (refundTransactions.existsBySourceTransactionIdAndStatus(
                source.getId(), RefundTransactionStatus.SUCCESS)) {
            throw new RefundTransactionConflictException(
                    "Giao dịch gốc đã có một lần hoàn tiền thành công");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new RefundTransactionValidationException("Số tiền hoàn phải lớn hơn 0");
        }
        if (source.getAmount() == null || amount.compareTo(source.getAmount()) > 0) {
            throw new RefundTransactionValidationException(
                    "Số tiền hoàn vượt quá giao dịch gốc");
        }

        RefundTransaction refund = RefundTransaction.success(invoiceId, source.getId(), amount,
                parseMethod(methodCode), reasonCode, blankToNull(reasonNote), blankToNull(gatewayRef),
                evidenceUrl.trim(), blankToNull(note), handledByAccountId, Instant.now());
        try {
            refundTransactions.saveAndFlush(refund);
        } catch (DataIntegrityViolationException ex) {
            throw new RefundTransactionConflictException(
                    "Giao dịch gốc đã có một lần hoàn tiền thành công");
        }
        recordHandoverAudit(refund, handledByAccountId);
        return RefundTransactionView.from(refund);
    }

    /** Khách xác nhận đã nhận; gọi lặp lại trả bản ghi cũ, không audit trùng. */
    @Transactional
    public RefundTransactionView confirm(Long invoiceId, Long refundTransactionId, Long actorAccountId) {
        RefundTransaction refund = findForInvoice(invoiceId, refundTransactionId, true);
        if (refund.getStatus() != RefundTransactionStatus.SUCCESS || refund.getDisbursedAt() == null) {
            throw new RefundTransactionConflictException(
                    "Khoản hoàn chưa được xác nhận đã đưa tiền");
        }
        if (refund.markCustomerConfirmed(Instant.now())) {
            refundTransactions.save(refund);
            Map<String, Object> oldValue = new LinkedHashMap<>();
            oldValue.put("customerConfirmedAt", null);
            Map<String, Object> newValue = new LinkedHashMap<>();
            newValue.put("customerConfirmedAt", refund.getCustomerConfirmedAt());
            auditRecorder.record(new AuditEntry(AuditAction.REFUND_CUSTOMER_CONFIRMED,
                    AuditEntityType.REFUND_TRANSACTION, String.valueOf(refund.getId()),
                    oldValue, newValue, null, actorAccountId));
        }
        return RefundTransactionView.from(refund);
    }

    /** Khách báo chưa nhận; ghi audit để mở đối soát, không tạo thêm refund. */
    @Transactional
    public RefundDisputeResult dispute(Long invoiceId, Long refundTransactionId, String note,
            Long actorAccountId) {
        if (note == null || note.isBlank()) {
            throw new RefundTransactionValidationException("Cần ghi chú khi báo chưa nhận tiền");
        }
        RefundTransaction refund = findForInvoice(invoiceId, refundTransactionId, true);
        if (refund.getStatus() != RefundTransactionStatus.SUCCESS || refund.getDisbursedAt() == null) {
            throw new RefundTransactionConflictException(
                    "Khoản hoàn chưa được xác nhận đã đưa tiền");
        }
        boolean newlyDisputed = refund.markDisputed(Instant.now(), note.trim());
        if (newlyDisputed) {
            refundTransactions.save(refund);
            Map<String, Object> newValue = new LinkedHashMap<>();
            newValue.put("disputed", true);
            newValue.put("note", note.trim());
            auditRecorder.record(new AuditEntry(AuditAction.REFUND_CUSTOMER_DISPUTED,
                    AuditEntityType.REFUND_TRANSACTION, String.valueOf(refund.getId()),
                    Map.of("disputed", false), newValue, note.trim(), actorAccountId));
        }
        return new RefundDisputeResult(RefundTransactionView.from(refund), newlyDisputed);
    }

    private PaymentTransaction findSource(Long invoiceId, Long sourceTransactionId) {
        if (sourceTransactionId != null) {
            return paymentTransactions.findByIdForUpdate(sourceTransactionId)
                    .orElseThrow(() -> new RefundTransactionValidationException(
                            "Không tìm thấy giao dịch gốc"));
        }
        return paymentTransactions
                .findFirstByInvoiceIdAndDirectionAndStatusInOrderByPaidAtDesc(
                        invoiceId, PaymentDirection.PAY,
                        List.of(PaymentStatus.SUCCESS, PaymentStatus.DUPLICATE))
                .orElseThrow(() -> new RefundTransactionValidationException(
                        "Hoá đơn chưa có giao dịch tiền vào có thể hoàn"));
    }

    private RefundTransaction findForInvoice(Long invoiceId, Long refundTransactionId,
            boolean lock) {
        RefundTransaction refund;
        if (refundTransactionId != null) {
            refund = lock
                    ? refundTransactions.findByIdForUpdate(refundTransactionId)
                            .orElseThrow(RefundTransactionNotFoundException::new)
                    : refundTransactions.findByIdAndInvoiceId(refundTransactionId, invoiceId)
                            .orElseThrow(RefundTransactionNotFoundException::new);
        } else {
            refund = refundTransactions
                    .findFirstByInvoiceIdAndStatusOrderByDisbursedAtDesc(
                            invoiceId, RefundTransactionStatus.SUCCESS)
                    .orElseThrow(RefundTransactionNotFoundException::new);
        }
        if (!invoiceId.equals(refund.getInvoiceId())) {
            throw new RefundTransactionNotFoundException();
        }
        return refund;
    }

    private void recordHandoverAudit(RefundTransaction refund, Long actorAccountId) {
        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("sourceTransactionId", refund.getSourceTransactionId());
        newValue.put("amount", refund.getAmount());
        newValue.put("method", refund.getMethod().name());
        newValue.put("reasonCode", refund.getReasonCode().name());
        newValue.put("status", refund.getStatus().dbValue());
        newValue.put("disbursedAt", refund.getDisbursedAt());
        auditRecorder.record(new AuditEntry(AuditAction.MANUAL_REFUND_RECORDED,
                AuditEntityType.REFUND_TRANSACTION, String.valueOf(refund.getId()),
                null, newValue, refund.getReasonCode().name(), actorAccountId));
    }

    private static void validateEvidenceUrl(String evidenceUrl) {
        if (evidenceUrl == null || evidenceUrl.isBlank()) {
            throw new RefundTransactionValidationException("Cần chứng từ đã đưa tiền");
        }
        try {
            URI uri = URI.create(evidenceUrl.trim());
            if (!uri.isAbsolute() || uri.getHost() == null
                    || (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("unsupported evidence URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new RefundTransactionValidationException(
                    "Chứng từ phải là URL http/https hợp lệ");
        }
    }

    private static RefundMethod parseMethod(String methodCode) {
        try {
            return RefundMethod.valueOf(methodCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RefundTransactionValidationException("Phương thức hoàn tiền không hợp lệ");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
