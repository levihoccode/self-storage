package vn.lemar.selfstorage.payment.application.dto;

import java.math.BigDecimal;
import vn.lemar.selfstorage.payment.domain.RefundObligationLine;

public record RefundObligationLineView(
        Long id,
        String componentCode,
        String direction,
        BigDecimal amount,
        String label,
        String sourceType,
        Long sourceId,
        String note,
        int sortOrder) {

    public static RefundObligationLineView from(RefundObligationLine line) {
        return new RefundObligationLineView(line.getId(), line.getComponentCode(),
                line.getDirection().name(), line.getAmount(), line.getLabel(), line.getSourceType(),
                line.getSourceId(), line.getNote(), line.getSortOrder());
    }
}
