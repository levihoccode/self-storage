package vn.lemar.selfstorage.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.identity.domain.EmailVerificationToken;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Optional<EmailVerificationToken> findByAccountIdAndConsumedAtIsNull(Long accountId);
}