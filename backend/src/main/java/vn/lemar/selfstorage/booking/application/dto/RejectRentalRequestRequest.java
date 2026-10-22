package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.lemar.selfstorage.booking.domain.RentalRequestRejectReason;

/**
 * Body từ chối yêu cầu đặt kho (B1 phần 4, spec draft.md §1.1) — reason theo bộ chuẩn, {@code OTHER}
 * bắt buộc kèm ghi chú (service kiểm tra, trả 400).
 */
public record RejectRentalRequestRequest(

        @NotNull
        @Schema(description = "Lý do từ chối theo bộ chuẩn", example = "UNIT_UNAVAILABLE")
        RentalRequestRejectReason rejectReason,

        @Size(max = 2000)
        @Schema(description = "Ghi chú (bắt buộc khi lý do là OTHER)",
                example = "Khoang đã có khách cọc")
        String rejectNote
) {
}
