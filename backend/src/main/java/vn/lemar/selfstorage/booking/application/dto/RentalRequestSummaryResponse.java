package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import vn.lemar.selfstorage.booking.domain.RentalRequest;

/**
 * Một dòng trong hàng đợi FM — không có field facility (spec draft.md NOTES §1.1:
 * FM chỉ thấy cơ sở của mình).
 */
public record RentalRequestSummaryResponse(
        @Schema(description = "Id yêu cầu") Long id,
        @Schema(description = "Tên khách hàng") String customerName,
        @Schema(description = "Email đã normalize") String customerEmail,
        @Schema(description = "Số điện thoại") String customerPhone,
        @Schema(description = "Loại khoang khách chọn") Long unitTypeId,
        @Schema(description = "Ngày bắt đầu thuê") LocalDate startDate,
        @Schema(description = "Số tháng thuê") Integer period,
        @Schema(description = "Trạng thái", example = "Pending") String status,
        @Schema(description = "Thời điểm tạo") Instant createdAt,
        @Schema(description = "Hạn xử lý") Instant expiresAt
) {

    public static RentalRequestSummaryResponse from(RentalRequest request) {
        return new RentalRequestSummaryResponse(request.getId(), request.getCustomerName(),
                request.getNormalizedCustomerEmail(), request.getCustomerPhone(),
                request.getUnitTypeId(), request.getStartDate(), request.getPeriod(),
                request.getStatus().name(), request.getCreatedAt(), request.getExpiresAt());
    }
}
