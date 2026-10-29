package vn.lemar.selfstorage.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình ZaloPay. Không đặt {@code @NotBlank} để app vẫn boot khi provider mặc định là VNPay;
 * adapter kiểm tra giá trị khi thực sự được dùng.
 */
@ConfigurationProperties(prefix = "zalopay")
public record ZaloPayProperties(
        String appId,
        String key1,
        String key2,
        String createUrl,
        String queryUrl,
        String callbackUrl,
        String redirectUrl) {
}
