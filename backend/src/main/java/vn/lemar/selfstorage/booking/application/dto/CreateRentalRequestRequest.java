package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Form yêu cầu đặt kho public (B1, spec draft.md §1.1).
 */
public record CreateRentalRequestRequest(

        @NotNull
        @Schema(description = "Chi nhánh khách chọn", example = "1")
        Long facilityId,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "Tên khách hàng", example = "Nguyen Van A")
        String customerName,

        @NotBlank
        @Email
        @Size(max = 255)
        @Schema(description = "Email (tự động trim + lowercase trước khi lưu)",
                example = "Khach@Example.com")
        String customerEmail,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 .\\-()]{7,20}$",
                message = "Số điện thoại không đúng định dạng hỗ trợ")
        @Schema(description = "Số điện thoại (7-20 ký tự số/dấu + ()-.)", example = "0901234567")
        String customerPhone,

        @NotNull
        @Schema(description = "Loại khoang khách chọn", example = "1")
        Long unitTypeId,

        @NotNull
        @Schema(description = "Ngày bắt đầu thuê (ISO, >= hôm nay theo Asia/Ho_Chi_Minh)",
                example = "2026-11-01")
        LocalDate startDate,

        @NotNull
        @Min(1)
        @Schema(description = "Số tháng thuê (số nguyên dương)", example = "6")
        Integer period
) {
}
