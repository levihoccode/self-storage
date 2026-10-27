package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Một đề xuất khoang trên trang "Kiểm tra kho của tôi" của khách. */
@Schema(description = "Đề xuất khoang của khách, kèm thông tin khoang/cơ sở và số lần đã từ chối của đơn")
public record CustomerProposalResponse(
        @Schema(description = "Id proposal", example = "1") Long id,
        @Schema(description = "Id đơn thuê — cũng là mã đơn hiển thị", example = "1") Long orderId,
        @Schema(description = "Pending / Agreed / Rejected / Canceled", example = "Pending") String status,
        @Schema(description = "Lý do khách từ chối (nếu có)") String note,
        @Schema(description = "Thời điểm FM đề xuất") Instant createdAt,
        @Schema(description = "Id khoang", example = "1") Long unitId,
        @Schema(description = "Mã khoang", example = "A-01") String unitCode,
        @Schema(description = "Kích thước khoang") String unitSize,
        @Schema(description = "Loại khoang", example = "Small") String unitTypeName,
        @Schema(description = "Id cơ sở", example = "1") Long facilityId,
        @Schema(description = "Tên cơ sở", example = "Self Storage Quận 7") String facilityName,
        @Schema(description = "Địa chỉ cơ sở") String facilityAddress,
        @Schema(description = "Giá thuê tháng hiệu lực: giá riêng của khoang, không có thì giá loại khoang",
                example = "500000.00") BigDecimal monthlyPrice,
        @Schema(description = "Số proposal Rejected của đơn", example = "0") long rejectionCount,
        @Schema(description = "Ngưỡng `proposal.max_rejection_count` — chạm ngưỡng thì đơn bị huỷ", example = "3")
        int maxRejectionCount) {
}
