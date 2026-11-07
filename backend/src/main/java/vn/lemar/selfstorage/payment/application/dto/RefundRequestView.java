package vn.lemar.selfstorage.payment.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.lemar.selfstorage.payment.domain.RefundRequest;

public record RefundRequestView(
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
        Long requestedByAccountId,
        Instant createdAt,
        Instant updatedAt,
        List<RefundRequestLineView> lines) {

    public static RefundRequestView from(RefundRequest request) {
        return new RefundRequestView(request.getId(), request.getInvoiceId(), request.getOrderId(),
                request.getAmount(), request.getReasonCode(), request.getReasonNote(),
                request.getStatus().dbValue(), request.getCalculationVersion(),
                request.getCalculationInput(), request.getFormulaText(),
                request.getRequestedByAccountId(), request.getCreatedAt(), request.getUpdatedAt(),
                request.getLines().stream().map(RefundRequestLineView::from).toList());
    }
}
