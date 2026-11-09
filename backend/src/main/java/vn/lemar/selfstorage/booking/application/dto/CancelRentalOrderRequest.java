package vn.lemar.selfstorage.booking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body FM huỷ đơn thuê (#142, lý do do FM nhập). {@code reason} đi vào {@code cancel_reason}, audit và
 * notification gửi khách + FM; {@code note} chỉ là ghi chú nội bộ trong audit.
 */
public record CancelRentalOrderRequest(

        @NotBlank
        @Size(max = 255)
        @Schema(description = "Lý do huỷ đơn — hiển thị cho khách trong notification",
                example = "Khách không còn nhu cầu thuê khoang")
        String reason,

        @Size(max = 500)
        @Schema(description = "Ghi chú nội bộ, chỉ lưu audit — không gửi khách", example = "Đã gọi 3 lần")
        String note
) {
}
