package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Kết quả từ chối RentalRequest (Pending sang Rejected — trạng thái cuối, không sang B2).
 * Service dựng response từ giá trị conditional update (nguồn sự thật là DB, không phải entity).
 */
public record RejectRentalRequestResponse(
        @Schema(description = "Id yêu cầu") Long id,
        @Schema(description = "Trạng thái", example = "Rejected") String status,
        @Schema(description = "Lý do từ chối", example = "UNIT_UNAVAILABLE") String rejectReason,
        @Schema(description = "Thời điểm FM từ chối") Instant respondedAt
) {
}
