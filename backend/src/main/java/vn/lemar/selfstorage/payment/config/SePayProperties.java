package vn.lemar.selfstorage.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình SePay. Không đặt {@code @NotBlank} để app vẫn boot khi provider mặc định là VNPay;
 * adapter kiểm tra giá trị khi thực sự được dùng.
 */
@ConfigurationProperties(prefix = "sepay")
public record SePayProperties(
        String bank,
        String accountNumber,
        String accountHolder,
        String qrBaseUrl,
        String webhookSecret) {
}
