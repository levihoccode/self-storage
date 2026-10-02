package vn.lemar.selfstorage.identity.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.lemar.selfstorage.identity.domain.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Optional<EmailVerificationToken> findByAccountIdAndConsumedAtIsNull(Long accountId);

    /**
     * Đánh dấu token đã dùng bằng conditional update (single-use chặt).
     * Trả về 1 nếu thắng (token chưa dùng và chưa hết hạn), 0 nếu không.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerificationToken t set t.consumedAt = :now "
            + "where t.id = :id and t.consumedAt is null and t.expiresAt > :now")
    int consumeIfValid(@Param("id") Long id, @Param("now") Instant now);
}