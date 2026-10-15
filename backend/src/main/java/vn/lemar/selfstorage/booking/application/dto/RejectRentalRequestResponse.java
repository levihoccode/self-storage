package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.lemar.selfstorage.booking.domain.RentalRequest;

/**
 * Kết quả từ chối RentalRequest (Pending sang Rejected — trạng thái cuối, không sang B2).
 */
public record RejectRentalRequestResponse(
        @Schema(description = "Id yêu cầu") Long id,
        @Schema(description = "Trạng thái", example = "Rejected") String status,
        @Schema(description = "Lý do từ chối", example = "UNIT_UNAVAILABLE") String rejectReason,
        @Schema(description = "Thời điểm FM từ chối") Instant respondedAt
) {

    public static RejectRentalRequestResponse from(RentalRequest request) {
        return new RejectRentalRequestResponse(request.getId(), request.getStatus().name(),
                request.getRejectReason() == null ? null : request.getRejectReason().name(),
                request.getRespondedAt());
    }
}
