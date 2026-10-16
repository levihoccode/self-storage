package vn.lemar.selfstorage.media.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình Cloudinary — bind trực tiếp từ env {@code CLOUDINARY_NAME / CLOUDINARY_KEY /
 * CLOUDINARY_SECRET} qua relaxed binding (không cần khai báo trong `application.yml`).
 *
 * <p>Thiếu là app chết ngay lúc khởi động — cùng chủ đích với {@code VNPAY_*}: thà chết lúc boot
 * còn hơn lỗi giữa luồng upload.
 */
@Validated
@ConfigurationProperties(prefix = "cloudinary")
public record CloudinaryProperties(
        @NotBlank String name,
        @NotBlank String key,
        @NotBlank String secret) {}
