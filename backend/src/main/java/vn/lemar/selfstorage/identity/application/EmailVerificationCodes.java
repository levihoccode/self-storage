package vn.lemar.selfstorage.identity.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.lemar.selfstorage.identity.application.exception.InvalidVerificationCodeException;
import vn.lemar.selfstorage.identity.application.exception.TooManyVerificationAttemptsException;
import vn.lemar.selfstorage.identity.application.exception.VerificationResendTooEarlyException;

/**
 * Kho mã xác minh email trên Redis (spec-gap #139): mã 6 số, TTL cấu hình
 * ({@code security.email-verification.ttl}, mặc định 10 phút), tối đa
 * {@code security.email-verification.max-attempts} lần nhập sai (mặc định 5), gửi lại
 * cách nhau tối thiểu {@code security.email-verification.resend-cooldown} (mặc định 60 giây).
 *
 * <p>Key:
 * <ul>
 *   <li>{@code auth:verify-code:<email>} — hash {@code hash} (BCrypt của mã, không lưu plaintext)
 *       + {@code attempts}; TTL = thời hạn mã, ghi lại mỗi lần phát mã mới.</li>
 *   <li>{@code auth:verify-cooldown:<email>} — set NX với TTL = cooldown, chặn spam resend.</li>
 * </ul>
 *
 * <p>Không fail-open: Redis lỗi thì register / verify / resend trả lỗi thay vì bỏ qua kiểm mã —
 * mã xác minh là hàng rào chống chiếm tài khoản.
 */
@Service
public class EmailVerificationCodes {

    static final int CODE_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_KEY_PREFIX = "auth:verify-code:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:verify-cooldown:";
    private static final String FIELD_HASH = "hash";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final Duration ttl;
    private final int maxAttempts;
    private final Duration resendCooldown;

    public EmailVerificationCodes(
            StringRedisTemplate redis,
            PasswordEncoder passwordEncoder,
            @Value("${security.email-verification.ttl:PT10M}") Duration ttl,
            @Value("${security.email-verification.max-attempts:5}") int maxAttempts,
            @Value("${security.email-verification.resend-cooldown:PT60S}") Duration resendCooldown) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Email verification TTL must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Email verification max attempts must be positive");
        }
        if (resendCooldown.isNegative()) {
            throw new IllegalArgumentException("Email verification resend cooldown must not be negative");
        }
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
        this.ttl = ttl;
        this.maxAttempts = maxAttempts;
        this.resendCooldown = resendCooldown;
    }

    /** Phát mã mới (ghi đè mã cũ nếu có) và trả mã plaintext để caller gửi email. */
    public String issue(String normalizedEmail) {
        String code = generateCode();
        String key = CODE_KEY_PREFIX + normalizedEmail;
        redis.opsForHash().put(key, FIELD_HASH, passwordEncoder.encode(code));
        redis.opsForHash().put(key, FIELD_ATTEMPTS, "0");
        redis.expire(key, ttl);
        return code;
    }

    /** Chặn gửi lại mã quá nhanh; gọi trước {@link #issue} ở luồng resend. */
    public void markResendCooldown(String normalizedEmail) {
        Boolean fresh = redis.opsForValue()
                .setIfAbsent(COOLDOWN_KEY_PREFIX + normalizedEmail, "1", resendCooldown);
        if (!Boolean.TRUE.equals(fresh)) {
            throw new VerificationResendTooEarlyException();
        }
    }

    /**
     * Kiểm mã: sai thì tăng bộ đếm; đủ {@code max-attempts} thì khoá (429) tới khi resend hoặc
     * mã hết hạn. Mã đúng bị xoá ngay (dùng một lần). Mã không tồn tại / hết hạn → 400.
     */
    public void verify(String normalizedEmail, String code) {
        String key = CODE_KEY_PREFIX + normalizedEmail;
        Map<Object, Object> state = redis.opsForHash().entries(key);
        if (state.isEmpty()) {
            throw new InvalidVerificationCodeException();
        }
        if (parseAttempts(state.get(FIELD_ATTEMPTS)) >= maxAttempts) {
            throw new TooManyVerificationAttemptsException();
        }
        Object hash = state.get(FIELD_HASH);
        if (hash == null || !passwordEncoder.matches(code, hash.toString())) {
            Long updated = redis.opsForHash().increment(key, FIELD_ATTEMPTS, 1);
            if (updated != null && updated >= maxAttempts) {
                throw new TooManyVerificationAttemptsException();
            }
            throw new InvalidVerificationCodeException();
        }
        redis.delete(key);
    }

    private static int parseAttempts(Object raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String generateCode() {
        return String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(1_000_000));
    }
}
