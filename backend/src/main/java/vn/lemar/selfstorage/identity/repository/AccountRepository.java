package vn.lemar.selfstorage.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.identity.domain.Account;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);
}