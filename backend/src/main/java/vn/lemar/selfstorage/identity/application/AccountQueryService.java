package vn.lemar.selfstorage.identity.application;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.lemar.selfstorage.identity.application.exception.AccountNotFoundException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

/**
 * Public API cho module khác tra account khi chỉ có email (từ JWT) hoặc id.
 */
@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;

    public AccountQueryService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public Long requireIdByEmail(String email) {
        return accountRepository.findByEmail(Account.normalize(email))
                .orElseThrow(AccountNotFoundException::new)
                .getId();
    }

    /**
     * Id account theo email (đã normalize); {@code Optional.empty()} khi chưa có account.
     *
     * <p>Dùng thay {@link #requireIdByEmail} khi caller đang trong transaction và "chưa có account"
     * là nhánh bình thường: exception ném ra từ method {@code @Transactional} này sẽ mark cả
     * transaction đang chạy rollback-only, commit sau đó nổ {@code UnexpectedRollbackException}
     * dù caller đã catch.
     */
    @Transactional(readOnly = true)
    public Optional<Long> findIdByEmail(String email) {
        return accountRepository.findByEmail(Account.normalize(email)).map(Account::getId);
    }

    @Transactional(readOnly = true)
    public String requireEmailById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new)
                .getEmail();
    }

    /** Account của email nếu đã xác minh; chưa có account hoặc chưa xác minh → rỗng. */
    @Transactional(readOnly = true)
    public Optional<VerifiedAccount> findVerifiedByEmail(String email) {
        return accountRepository.findByEmail(Account.normalize(email))
                .filter(Account::isVerified)
                .map(account -> new VerifiedAccount(account.getId(), account.getEmail()));
    }

    /** Account theo id nếu đã xác minh; không tồn tại hoặc chưa xác minh → rỗng. */
    @Transactional(readOnly = true)
    public Optional<VerifiedAccount> findVerifiedById(Long accountId) {
        return accountRepository.findById(accountId)
                .filter(Account::isVerified)
                .map(account -> new VerifiedAccount(account.getId(), account.getEmail()));
    }
}
