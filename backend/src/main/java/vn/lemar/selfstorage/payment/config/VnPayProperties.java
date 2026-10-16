package vn.lemar.selfstorage.payment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình VNPay, đọc từ biến môi trường qua `application.yml`.
 *
 * @param tmnCode    mã website đăng ký trên hệ thống VNPay
 * @param hashSecret chuỗi bí mật dùng ký và kiểm checksum
 * @param payUrl     endpoint cổng thanh toán
 * @param returnUrl  nơi VNPay đẩy trình duyệt khách về sau khi thanh toán
 */
@Validated
@ConfigurationProperties(prefix = "vnpay")
public record VnPayProperties(
        @NotBlank String tmnCode,
        @NotBlank String hashSecret,
        @NotBlank String payUrl,
        @NotBlank String returnUrl) {}
