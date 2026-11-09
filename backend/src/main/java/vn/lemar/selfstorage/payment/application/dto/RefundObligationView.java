package vn.lemar.selfstorage.payment.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.lemar.selfstorage.payment.domain.RefundObligation;

public record RefundObligationView(
        Long id,
        Long invoiceId,
        Long orderId,
        BigDecimal amount,
        String reasonCode,
        String reasonNote,
        String status,
        String calculationVersion,
        JsonNode calculationInput,
        String formulaText,
        Long createdByAccountId,
        Instant createdAt,
        Instant updatedAt,
        List<RefundObligationLineView> lines) {

    public static RefundObligationView from(RefundObligation obligation) {
        return new RefundObligationView(obligation.getId(), obligation.getInvoiceId(), obligation.getOrderId(),
                obligation.getAmount(), obligation.getReasonCode(), obligation.getReasonNote(),
                obligation.getStatus().dbValue(), obligation.getCalculationVersion(),
                obligation.getCalculationInput(), obligation.getFormulaText(),
                obligation.getCreatedByAccountId(), obligation.getCreatedAt(), obligation.getUpdatedAt(),
                obligation.getLines().stream().map(RefundObligationLineView::from).toList());
    }
}
