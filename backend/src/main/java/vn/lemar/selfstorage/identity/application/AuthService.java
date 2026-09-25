package vn.lemar.selfstorage.identity.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.identity.application.dto.LoginRequest;
import vn.lemar.selfstorage.identity.application.dto.LoginResponse;
import vn.lemar.selfstorage.identity.application.exception.AccountNotAllowedException;
import vn.lemar.selfstorage.identity.application.exception.InvalidCredentialsException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = Account.normalize(request.email());

        Account account = accountRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!account.isLoginAllowed()) {
            throw new AccountNotAllowedException(account.getStatus());
        }

        // TODO: thay bằng JWT thật khi có JwtService (generate access token + refresh token)
        String fakeToken = "dev-token-" + account.getId();

        return new LoginResponse(
                account.getId(),
                account.getEmail(),
                account.getRole().getName(),
                fakeToken
        );
    }
}