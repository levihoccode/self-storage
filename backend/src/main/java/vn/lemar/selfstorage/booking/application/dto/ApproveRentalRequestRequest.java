package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Body duyệt yêu cầu đặt kho (B1, spec draft.md §1.1) — FM chọn khoang cụ thể.
 */
public record ApproveRentalRequestRequest(

        @NotNull
        @Schema(description = "Khoang gán cho yêu cầu (phải đang Available)", example = "10")
        Long unitId
) {
}
