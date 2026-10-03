package vn.lemar.selfstorage.identity.application;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.lemar.selfstorage.identity.application.dto.LoginRequest;
import vn.lemar.selfstorage.identity.application.dto.LoginResponse;
import vn.lemar.selfstorage.identity.application.dto.MeResponse;
import vn.lemar.selfstorage.identity.application.exception.InvalidCredentialsException;
import vn.lemar.selfstorage.identity.application.exception.AccountNotAllowedException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.AccountStatus;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginNormalizesEmailAndReturnsIssuedToken() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(42L);
        when(account.getEmail()).thenReturn("customer@example.com");
        when(account.getPasswordHash()).thenReturn("password-hash");
        when(account.isLoginAllowed()).thenReturn(true);
        when(accountRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password", "password-hash")).thenReturn(true);
        when(jwtService.issueAccessToken(account)).thenReturn("signed.jwt.token");

        LoginResponse response = authService.login(new LoginRequest(" Customer@Example.com ", "password"));

        assertThat(response.accountId()).isEqualTo(42L);
        assertThat(response.email()).isEqualTo("customer@example.com");
        assertThat(response.token()).isEqualTo("signed.jwt.token");
        verify(accountRepository).findByEmail("customer@example.com");
        verify(jwtService).issueAccessToken(account);
    }

    @Test
    void loginRejectsIncorrectPasswordWithoutIssuingToken() {
        Account account = mock(Account.class);
        when(account.getPasswordHash()).thenReturn("password-hash");
        when(accountRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "password-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("customer@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = AccountStatus.class,
            names = {"UNVERIFIED", "LOCKED", "BANNED"})
    void loginRejectsEveryNonActiveStatus(AccountStatus status) {
        Account account = mock(Account.class);
        when(account.getPasswordHash()).thenReturn("password-hash");
        when(account.isLoginAllowed()).thenReturn(false);
        when(account.getStatus()).thenReturn(status);
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password", "password-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "password")))
                .isInstanceOf(AccountNotAllowedException.class)
                .hasMessageNotContaining("Email hoặc mật khẩu");
    }

    @Test
    void meReturnsEmailAndRoleForCurrentAccount() {
        Account account = new Account("Customer@Example.com ", "hash", new Role("CUSTOMER"));
        when(accountRepository.findWithRoleByEmail("customer@example.com")).thenReturn(Optional.of(account));

        MeResponse response = authService.me("customer@example.com");

        assertThat(response.email()).isEqualTo("customer@example.com");
        assertThat(response.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void meRejectsUnknownAccount() {
        when(accountRepository.findWithRoleByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me("ghost@example.com"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}