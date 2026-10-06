package vn.lemar.selfstorage;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Envelope lỗi chuẩn cho mọi 4xx/5xx: {@code message} hiển thị được + {@code timestamp}.
 */
@Schema(description = "Envelope lỗi — mọi 4xx/5xx")
public record ApiError(
        @Schema(description = "Câu thông báo hiển thị trực tiếp cho người dùng")
        String message,

        @Schema(description = "Thời điểm lỗi (ISO-8601, UTC)",
                example = "2026-10-04T08:14:27.947381536Z")
        String timestamp) {

    public static ApiError now(String message) {
        return new ApiError(message, Instant.now().toString());
    }
}
