package vn.lemar.selfstorage.identity.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.lemar.selfstorage.identity.application.exception.UnauthenticatedException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

/**
 * Doc Account hien tai tu SecurityContext (principal = accountId, do
 * JwtAuthenticationFilter set qua IdentityWebSecuritySupport).
 */
@Component
public class CurrentAccountProvider {

    private final AccountRepository accountRepository;

    public CurrentAccountProvider(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account getCurrentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String rawAccountId)) {
            throw new UnauthenticatedException();
        }

        Long accountId;
        try {
            accountId = Long.valueOf(rawAccountId);
        } catch (NumberFormatException e) {
            throw new UnauthenticatedException();
        }

        return accountRepository.findById(accountId)
                .orElseThrow(UnauthenticatedException::new);
    }
}