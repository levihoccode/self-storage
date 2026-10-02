package vn.lemar.selfstorage.identity.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

@Component
public class AccountJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private static final String ACTIVE_ACCOUNT_AUTHORITY = "ACCOUNT_ACTIVE";

    private final AccountRepository accountRepository;

    public AccountJwtAuthenticationConverter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public JwtAuthenticationToken convert(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new InvalidBearerTokenException("JWT is missing the email claim");
        }

        Account account = accountRepository.findWithRoleByEmail(Account.normalize(email))
                .orElseThrow(() -> new InvalidBearerTokenException("Account for JWT was not found"));

        List<GrantedAuthority> authorities = new ArrayList<>();
        if (account.isLoginAllowed()) {
            authorities.add(new SimpleGrantedAuthority(ACTIVE_ACCOUNT_AUTHORITY));
            authorities.add(new SimpleGrantedAuthority("ROLE_" + account.getRole().getName()));
        }

        return new JwtAuthenticationToken(jwt, authorities, account.getEmail());
    }
}