package vn.lemar.selfstorage.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình payOS. Không đặt {@code @NotBlank} để app vẫn boot khi đang dùng cổng khác; adapter kiểm
 * tra giá trị khi thực sự được dùng.
 *
 * @param clientId    header {@code x-client-id} của kênh thanh toán
 * @param apiKey      header {@code x-api-key}
 * @param checksumKey khoá HMAC-SHA256 ký request tạo link và xác thực webhook
 * @param baseUrl     gốc API merchant (payOS không có sandbox — chỉ production)
 * @param returnUrl   payOS chuyển trình duyệt khách về sau khi thanh toán
 * @param cancelUrl   payOS chuyển trình duyệt khách về khi khách huỷ trên trang thanh toán
 */
@ConfigurationProperties(prefix = "payos")
public record PayOsProperties(
        String clientId,
        String apiKey,
        String checksumKey,
        String baseUrl,
        String returnUrl,
        String cancelUrl) {
}
