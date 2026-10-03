package vn.lemar.selfstorage.identity.application;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.AccountStatus;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountJwtAuthenticationConverterTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountJwtAuthenticationConverter converter;

    @Test
    void rejectsJwtWithoutEmailClaim() {
        Jwt jwt = jwt(null);

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("email");
    }

    @Test
    void rejectsJwtForUnknownAccount() {
        when(accountRepository.findWithRoleByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(jwt("ghost@example.com")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void mapsActiveAccountToRoleAndActiveAuthorities() {
        Account account = new Account("Customer@Example.com ", "hash", new Role("CUSTOMER"));
        when(accountRepository.findWithRoleByEmail("customer@example.com")).thenReturn(Optional.of(account));

        JwtAuthenticationToken token = converter.convert(jwt(" Customer@Example.com "));

        assertThat(token.getName()).isEqualTo("customer@example.com");
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ACCOUNT_ACTIVE", "ROLE_CUSTOMER");
        verify(accountRepository).findWithRoleByEmail("customer@example.com");
    }

    @Test
    void bannedAccountGetsNoAuthorities() {
        Account account = new Account("customer@example.com", "hash", new Role("CUSTOMER"));
        account.setStatus(AccountStatus.BANNED);
        when(accountRepository.findWithRoleByEmail("customer@example.com")).thenReturn(Optional.of(account));

        JwtAuthenticationToken token = converter.convert(jwt("customer@example.com"));

        assertThat(token.getAuthorities()).isEmpty();
    }

    private static Jwt jwt(String email) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.build();
    }
}
