package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Kết quả duyệt RentalRequest (Pending sang Approved, B1 dừng ở đây — không tạo proposal/order).
 * Service dựng response từ giá trị conditional update (nguồn sự thật là DB, không phải entity).
 */
public record ApproveRentalRequestResponse(
        @Schema(description = "Id yêu cầu") Long id,
        @Schema(description = "Trạng thái", example = "Approved") String status,
        @Schema(description = "Khoang đã gán") Long unitId,
        @Schema(description = "Thời điểm FM duyệt") Instant respondedAt,
        @Schema(description = "Hạn xử lý (responded_at + account.claim_ttl_days)")
        Instant expiresAt
) {
}
