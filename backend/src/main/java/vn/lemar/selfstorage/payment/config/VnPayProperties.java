package vn.lemar.selfstorage.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình VNPay, đọc từ biến môi trường qua `application.yml`.
 *
 * @param tmnCode    mã website đăng ký trên hệ thống VNPay
 * @param hashSecret chuỗi bí mật dùng ký và kiểm checksum
 * @param payUrl     endpoint cổng thanh toán
 * @param returnUrl  nơi VNPay đẩy trình duyệt khách về sau khi thanh toán
 */
@ConfigurationProperties(prefix = "vnpay")
public record VnPayProperties(String tmnCode, String hashSecret, String payUrl, String returnUrl) {}
