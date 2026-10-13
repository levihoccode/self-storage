package vn.lemar.selfstorage.identity.application;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.lemar.selfstorage.identity.application.exception.UnauthenticatedException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

/** Lấy account của request hiện tại từ SecurityContext (principal là email). */
@Component
public class CurrentAccountProvider {

    private final AccountRepository accountRepository;

    public CurrentAccountProvider(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account getCurrentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            throw new UnauthenticatedException();
        }
        return accountRepository.findWithRoleByEmail(Account.normalize(authentication.getName()))
                .orElseThrow(UnauthenticatedException::new);
    }

    /**
     * Id account hiện tại. Module nghiệp vụ dùng cái này thay vì {@link #getCurrentAccount()} để
     * không phải tham chiếu entity {@code Account} — entity là nội bộ của identity.
     */
    public Long currentAccountId() {
        return getCurrentAccount().getId();
    }

    /** Khách đã xác minh email chưa — điều kiện chặn thao tác nghiệp vụ theo spec Flow 1 §1.2. */
    public boolean isCurrentAccountVerified() {
        return getCurrentAccount().isVerified();
    }
}