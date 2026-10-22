package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import vn.lemar.selfstorage.booking.domain.RentalRequest;

/**
 * Kết quả tạo RentalRequest (status Pending + expiresAt).
 */
public record RentalRequestResponse(
        @Schema(description = "Id yêu cầu") Long id,
        @Schema(description = "Trạng thái", example = "Pending") String status,
        @Schema(description = "Ngày bắt đầu thuê") LocalDate startDate,
        @Schema(description = "Hạn xử lý (created_at + request.pending_expiry_days)")
        Instant expiresAt
) {

    public static RentalRequestResponse from(RentalRequest request) {
        return new RentalRequestResponse(request.getId(), request.getStatus().name(),
                request.getStartDate(), request.getExpiresAt());
    }
}
