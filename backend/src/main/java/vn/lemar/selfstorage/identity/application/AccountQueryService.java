package vn.lemar.selfstorage.identity.application;

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

    @Transactional(readOnly = true)
    public String requireEmailById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new)
                .getEmail();
    }
}
