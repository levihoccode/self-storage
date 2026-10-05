package vn.lemar.selfstorage;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Envelope response chuẩn: mọi 2xx trả {@code message} cho FE hiển thị trực tiếp + {@code data}
 * theo từng route.
 */
@Schema(description = "Envelope response thành công — mọi 2xx")
public record ApiEnvelope<T>(
        @Schema(description = "Câu thông báo hiển thị trực tiếp cho người dùng",
                example = "Đăng nhập thành công")
        String message,

        @Schema(description = "Dữ liệu theo từng route")
        T data) {

    public static <T> ApiEnvelope<T> ok(String message, T data) {
        return new ApiEnvelope<>(message, data);
    }
}
