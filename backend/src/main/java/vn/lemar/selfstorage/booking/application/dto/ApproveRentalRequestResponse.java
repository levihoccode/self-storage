package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.lemar.selfstorage.booking.domain.RentalRequest;

/**
 * Kết quả duyệt RentalRequest (Pending sang Approved, B1 dừng ở đây — không tạo proposal/order).
 */
public record ApproveRentalRequestResponse(
        @Schema(description = "Id yêu cầu") Long id,
        @Schema(description = "Trạng thái", example = "Approved") String status,
        @Schema(description = "Khoang đã gán") Long unitId,
        @Schema(description = "Thời điểm FM duyệt") Instant respondedAt,
        @Schema(description = "Hạn xử lý (responded_at + account.claim_ttl_days)")
        Instant expiresAt
) {

    public static ApproveRentalRequestResponse from(RentalRequest request) {
        return new ApproveRentalRequestResponse(request.getId(), request.getStatus().name(),
                request.getUnitId(), request.getRespondedAt(), request.getExpiresAt());
    }
}
