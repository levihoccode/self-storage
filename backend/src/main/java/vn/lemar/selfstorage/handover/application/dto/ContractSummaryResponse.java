package vn.lemar.selfstorage.handover.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import vn.lemar.selfstorage.handover.repository.RentalContractReadRepository;

/** Một hợp đồng đang hiệu lực trong màn "Kho của tôi" của khách. */
public record ContractSummaryResponse(
        @Schema(description = "Id hợp đồng") Long id,
        @Schema(description = "Mã hợp đồng", example = "HD-Q7-0001") String code,
        @Schema(description = "Trạng thái hợp đồng", example = "Active") String status,
        @Schema(description = "Id đơn thuê gốc") Long orderId,
        @Schema(description = "Id khoang") Long unitId,
        @Schema(description = "Mã khoang", example = "A-01") String unitCode,
        @Schema(description = "Kích thước khoang (nhãn tự do)", example = "1.2 × 2 × 2.2 m") String unitSize,
        @Schema(description = "Loại khoang", example = "Small") String unitTypeName,
        @Schema(description = "Id cơ sở") Long facilityId,
        @Schema(description = "Tên cơ sở") String facilityName,
        @Schema(description = "Địa chỉ cơ sở") String facilityAddress,
        @Schema(description = "Giá thuê chốt lúc ký", example = "500000.00") BigDecimal monthlyPrice,
        @Schema(description = "Tiền cọc đã thu", example = "500000.00") BigDecimal depositAmount,
        @Schema(description = "Số tháng thuê", example = "6") Integer period,
        @Schema(description = "Ngày bắt đầu tính tiền") LocalDate startDate,
        @Schema(description = "Ngày hết hạn hợp đồng; null = chưa chốt") LocalDate endDate,
        @Schema(description = "Thời điểm ký") Instant signedAt,
        @Schema(description = "Đã quá `endDate` tính theo ngày ở Asia/Ho_Chi_Minh")
        boolean overdue,
        @Schema(description = "Còn `contract.expiring_soon_days` ngày trở xuống tới `endDate` (chưa quá hạn)")
        boolean expiringSoon
) {

    public static ContractSummaryResponse from(RentalContractReadRepository.ContractRow row, LocalDate today,
                                              int expiringSoonDays) {
        boolean overdue = row.endDate() != null && row.endDate().isBefore(today);
        boolean expiringSoon = !overdue && row.endDate() != null
                && !row.endDate().isAfter(today.plusDays(expiringSoonDays));
        return new ContractSummaryResponse(row.id(), row.code(), row.status(), row.orderId(),
                row.unitId(), row.unitCode(), row.unitSize(), row.unitTypeName(), row.facilityId(),
                row.facilityName(), row.facilityAddress(), row.monthlyPrice(), row.depositAmount(),
                row.period(), row.startDate(), row.endDate(), row.signedAt(), overdue, expiringSoon);
    }
}
