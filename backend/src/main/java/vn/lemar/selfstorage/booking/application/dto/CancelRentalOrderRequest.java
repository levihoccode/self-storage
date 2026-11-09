package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body FM huỷ đơn thuê (#142, lý do do FM nhập). Lý do được ghi vào {@code cancel_reason}, audit và
 * notification gửi khách + FM.
 */
public record CancelRentalOrderRequest(

        @NotBlank
        @Size(max = 255)
        @Schema(description = "Lý do huỷ đơn", example = "Khách không còn nhu cầu thuê khoang")
        String reason
) {
}
