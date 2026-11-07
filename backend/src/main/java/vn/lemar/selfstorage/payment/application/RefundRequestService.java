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
import vn.lemar.selfstorage.payment.application.dto.CreateRefundRequest;
import vn.lemar.selfstorage.payment.application.dto.RefundRequestLineInput;
import vn.lemar.selfstorage.payment.application.dto.RefundRequestView;
import vn.lemar.selfstorage.payment.application.exception.RefundRequestConflictException;
import vn.lemar.selfstorage.payment.application.exception.RefundRequestValidationException;
import vn.lemar.selfstorage.payment.domain.RefundLineDirection;
import vn.lemar.selfstorage.payment.domain.RefundRequest;
import vn.lemar.selfstorage.payment.domain.RefundRequestStatus;
import vn.lemar.selfstorage.payment.repository.RefundRequestRepository;

/**
 * Tạo nghĩa vụ hoàn tiền có breakdown. Đây chưa phải ledger chi tiền: refund_transactions vẫn
 * được ghi khi FS/FM handover.
 */
@Service
public class RefundRequestService {

    private static final Set<String> REASON_CODES = Set.of(
            "UNIT_UNAVAILABLE", "DUPLICATE_PAYMENT", "DEPOSIT_SURPLUS", "OTHER");
    private static final Set<String> COMPONENT_CODES = Set.of(
            "DEPOSIT_PAID", "DEPOSIT_REQUIRED", "UNIT_CHANGE_FEE", "UNPAID_RENT",
            "REPAIR_FEE", "CLEANING_FEE", "PRIOR_REFUND", "GOODWILL", "ADJUSTMENT", "OTHER");
    private static final List<RefundRequestStatus> ACTIVE = List.of(
            RefundRequestStatus.REQUESTED, RefundRequestStatus.APPROVED);

    private final RefundRequestRepository requests;
    private final AuditRecorder auditRecorder;

    public RefundRequestService(RefundRequestRepository requests, AuditRecorder auditRecorder) {
        this.requests = requests;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public RefundRequestView create(CreateRefundRequest input, Long actorAccountId) {
        validate(input);
        if (input.invoiceId() != null
                && requests.existsByInvoiceIdAndStatusIn(input.invoiceId(), ACTIVE)) {
            throw new RefundRequestConflictException(
                    "Invoice đã có refund request đang mở");
        }
        if (input.invoiceId() == null && input.orderId() != null
                && requests.existsByOrderIdAndInvoiceIdIsNullAndStatusIn(input.orderId(), ACTIVE)) {
            throw new RefundRequestConflictException(
                    "Đơn đã có refund request đang mở");
        }

        RefundRequest request = RefundRequest.create(input.invoiceId(), input.orderId(),
                input.reasonCode(), blankToNull(input.reasonNote()),
                input.calculationVersion(), input.calculationInput(), actorAccountId);
        int index = 0;
        for (RefundRequestLineInput line : input.lines()) {
            request.addLine(line.componentCode().trim().toUpperCase(Locale.ROOT),
                    parseDirection(line.direction()), line.amount(), line.label().trim(),
                    blankToNull(line.sourceType()), line.sourceId(), blankToNull(line.note()),
                    line.sortOrder() == null ? index : line.sortOrder());
            index++;
        }
        try {
            request.calculate();
            requests.saveAndFlush(request);
        } catch (DataIntegrityViolationException ex) {
            throw new RefundRequestConflictException(
                    "Invoice/đơn đã có refund request đang mở");
        } catch (IllegalArgumentException ex) {
            throw new RefundRequestValidationException(ex.getMessage());
        }

        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("invoiceId", request.getInvoiceId());
        newValue.put("orderId", request.getOrderId());
        newValue.put("amount", request.getAmount());
        newValue.put("reasonCode", request.getReasonCode());
        newValue.put("status", request.getStatus().dbValue());
        newValue.put("calculationVersion", request.getCalculationVersion());
        newValue.put("formulaText", request.getFormulaText());
        newValue.put("lineCount", request.getLines().size());
        auditRecorder.record(new AuditEntry(AuditAction.REFUND_REQUESTED,
                AuditEntityType.REFUND_REQUEST, String.valueOf(request.getId()),
                null, newValue, request.getReasonCode(), actorAccountId));
        return RefundRequestView.from(request);
    }

    private static void validate(CreateRefundRequest input) {
        if (input.invoiceId() == null && input.orderId() == null) {
            throw new RefundRequestValidationException(
                    "Phải có invoiceId hoặc orderId");
        }
        if (!REASON_CODES.contains(input.reasonCode())) {
            throw new RefundRequestValidationException("Lý do hoàn tiền không hợp lệ");
        }
        if ("OTHER".equals(input.reasonCode())
                && (input.reasonNote() == null || input.reasonNote().isBlank())) {
            throw new RefundRequestValidationException("Lý do OTHER phải có ghi chú");
        }
        for (RefundRequestLineInput line : input.lines()) {
            if (!COMPONENT_CODES.contains(line.componentCode().trim().toUpperCase(Locale.ROOT))) {
                throw new RefundRequestValidationException(
                        "componentCode không hợp lệ: " + line.componentCode());
            }
            if (line.amount() == null || line.amount().signum() <= 0) {
                throw new RefundRequestValidationException(
                        "amount của từng line phải lớn hơn 0");
            }
        }
    }

    private static RefundLineDirection parseDirection(String value) {
        try {
            return RefundLineDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RefundRequestValidationException("direction không hợp lệ: " + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
