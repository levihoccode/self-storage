package vn.lemar.selfstorage.payment.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.payment.application.dto.CreateRefundObligation;
import vn.lemar.selfstorage.payment.application.dto.RefundObligationLineInput;
import vn.lemar.selfstorage.payment.application.dto.RefundObligationView;
import vn.lemar.selfstorage.payment.application.exception.RefundObligationConflictException;
import vn.lemar.selfstorage.payment.application.exception.RefundObligationValidationException;
import vn.lemar.selfstorage.payment.domain.RefundLineDirection;
import vn.lemar.selfstorage.payment.domain.RefundObligation;
import vn.lemar.selfstorage.payment.domain.RefundObligationStatus;
import vn.lemar.selfstorage.payment.repository.RefundObligationRepository;

/**
 * Tạo nghĩa vụ hoàn tiền có breakdown. Đây chưa phải ledger chi tiền: refund_transactions vẫn
 * được ghi khi FS/FM handover.
 */
@Service
public class RefundObligationService {

    private static final Set<String> REASON_CODES = Set.of(
            "UNIT_UNAVAILABLE", "DUPLICATE_PAYMENT", "DEPOSIT_SURPLUS", "OTHER");
    private static final Set<String> COMPONENT_CODES = Set.of(
            "DEPOSIT_PAID", "DEPOSIT_REQUIRED", "UNIT_CHANGE_FEE", "UNPAID_RENT",
            "REPAIR_FEE", "CLEANING_FEE", "PRIOR_REFUND", "GOODWILL", "ADJUSTMENT", "OTHER");
    private static final List<RefundObligationStatus> ACTIVE = List.of(
            RefundObligationStatus.OPEN, RefundObligationStatus.APPROVED);

    private final RefundObligationRepository obligations;
    private final AuditRecorder auditRecorder;

    public RefundObligationService(RefundObligationRepository obligations, AuditRecorder auditRecorder) {
        this.obligations = obligations;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public RefundObligationView create(CreateRefundObligation input, Long actorAccountId) {
        validate(input);
        if (input.invoiceId() != null
                && obligations.existsByInvoiceIdAndStatusIn(input.invoiceId(), ACTIVE)) {
            throw new RefundObligationConflictException(
                    "Invoice đã có refund obligation đang mở");
        }
        RefundObligation obligation = RefundObligation.create(input.invoiceId(), input.orderId(),
                input.reasonCode(), blankToNull(input.reasonNote()),
                input.calculationVersion(), input.calculationInput(), actorAccountId);
        int index = 0;
        for (RefundObligationLineInput line : input.lines()) {
            obligation.addLine(line.componentCode().trim().toUpperCase(Locale.ROOT),
                    parseDirection(line.direction()), line.amount(), line.label().trim(),
                    blankToNull(line.sourceType()), line.sourceId(), blankToNull(line.note()),
                    line.sortOrder() == null ? index : line.sortOrder());
            index++;
        }
        try {
            obligation.calculate();
            obligations.saveAndFlush(obligation);
        } catch (DataIntegrityViolationException ex) {
            throw new RefundObligationConflictException(
                    "Invoice đã có refund obligation đang mở");
        } catch (IllegalArgumentException ex) {
            throw new RefundObligationValidationException(ex.getMessage());
        }

        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("invoiceId", obligation.getInvoiceId());
        newValue.put("orderId", obligation.getOrderId());
        newValue.put("amount", obligation.getAmount());
        newValue.put("reasonCode", obligation.getReasonCode());
        newValue.put("status", obligation.getStatus().dbValue());
        newValue.put("calculationVersion", obligation.getCalculationVersion());
        newValue.put("formulaText", obligation.getFormulaText());
        newValue.put("lineCount", obligation.getLines().size());
        auditRecorder.record(new AuditEntry(AuditAction.REFUND_OBLIGATION_CREATED,
                AuditEntityType.REFUND_OBLIGATION, String.valueOf(obligation.getId()),
                null, newValue, obligation.getReasonCode(), actorAccountId));
        return RefundObligationView.from(obligation);
    }

    private static void validate(CreateRefundObligation input) {
        if (input.invoiceId() == null && input.orderId() == null) {
            throw new RefundObligationValidationException(
                    "Phải có invoiceId hoặc orderId");
        }
        if (!REASON_CODES.contains(input.reasonCode())) {
            throw new RefundObligationValidationException("Lý do hoàn tiền không hợp lệ");
        }
        if ("OTHER".equals(input.reasonCode())
                && (input.reasonNote() == null || input.reasonNote().isBlank())) {
            throw new RefundObligationValidationException("Lý do OTHER phải có ghi chú");
        }
        for (RefundObligationLineInput line : input.lines()) {
            if (!COMPONENT_CODES.contains(line.componentCode().trim().toUpperCase(Locale.ROOT))) {
                throw new RefundObligationValidationException(
                        "componentCode không hợp lệ: " + line.componentCode());
            }
            if (line.amount() == null || line.amount().signum() <= 0) {
                throw new RefundObligationValidationException(
                        "amount của từng line phải lớn hơn 0");
            }
        }
    }

    private static RefundLineDirection parseDirection(String value) {
        try {
            return RefundLineDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RefundObligationValidationException("direction không hợp lệ: " + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
