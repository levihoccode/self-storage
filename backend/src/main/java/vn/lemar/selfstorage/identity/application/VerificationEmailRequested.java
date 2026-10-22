package vn.lemar.selfstorage.identity.application;

import java.time.Duration;

/**
 * Phát khi identity cần gửi email chứa mã xác minh cho account vừa đăng ký hoặc vừa yêu cầu
 * gửi lại mã.
 *
 * <p>Identity cố tình <b>không</b> phụ thuộc module notification (notification đã phụ thuộc
 * ngược lại identity — xem {@code package-info} hai module, ModuleStructureTest chặn cycle).
 * Module notification lắng nghe event này và gửi email. Gửi email vẫn theo cơ chế after-commit
 * của {@code EmailSender} — transaction rollback thì không gửi.
 */
public record VerificationEmailRequested(String email, String code, Duration validFor) {
}
