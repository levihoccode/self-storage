package vn.lemar.selfstorage.identify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.identity.application.AuthService;
import vn.lemar.selfstorage.identity.application.dto.LoginRequest;
import vn.lemar.selfstorage.identity.application.dto.RegisterRequest;
import vn.lemar.selfstorage.identity.application.dto.ResendVerificationRequest;
import vn.lemar.selfstorage.identity.application.dto.VerifyEmailRequest;
import vn.lemar.selfstorage.identity.application.exception.AccountNotAllowedException;
import vn.lemar.selfstorage.identity.application.exception.EmailAlreadyExistsException;
import vn.lemar.selfstorage.identity.application.exception.InvalidOrExpiredTokenException;
import vn.lemar.selfstorage.identity.domain.AccountStatus;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
@TestPropertySource(properties = "app.verify-base-url=http://localhost:8080/api/auth/verify-email")
class AuthRegisterVerifyTest {

    private static final Pattern TOKEN_IN_MAIL =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PASSWORD = "Abc12345";

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void registerVerifyThenLogin() {
        String email = "new-customer-" + System.nanoTime() + "@test.local";

        authService.register(new RegisterRequest(email, PASSWORD));

        var account = accountRepository.findByEmail(email).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.UNVERIFIED);

        String rawToken = captureTokenFromLastMail();
        authService.verifyEmail(new VerifyEmailRequest(rawToken));

        assertThat(accountRepository.findByEmail(email).orElseThrow().isLoginAllowed()).isTrue();

        var login = authService.login(new LoginRequest(email, PASSWORD));
        assertThat(login.email()).isEqualTo(email);
        assertThat(login.role()).isEqualTo("CUSTOMER");
        assertThat(login.accessToken()).isNotBlank();
    }

    @Test
    void loginBeforeVerifyIsRejected() {
        String email = "unverified-" + System.nanoTime() + "@test.local";
        authService.register(new RegisterRequest(email, PASSWORD));

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOf(AccountNotAllowedException.class);
    }

    @Test
    void duplicateRegisterThrowsConflict() {
        String email = "dup-" + System.nanoTime() + "@test.local";
        authService.register(new RegisterRequest(email, PASSWORD));

        assertThatThrownBy(() -> authService.register(new RegisterRequest(email, PASSWORD)))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void resendIssuesNewTokenAndAllowsVerify() {
        String email = "resend-" + System.nanoTime() + "@test.local";
        authService.register(new RegisterRequest(email, PASSWORD));
        String firstToken = captureTokenFromLastMail();

        authService.resendVerificationEmail(new ResendVerificationRequest(email));
        String secondToken = captureTokenFromLastMail();
        assertThat(secondToken).isNotEqualTo(firstToken);

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest(firstToken)))
                .isInstanceOf(InvalidOrExpiredTokenException.class);

        authService.verifyEmail(new VerifyEmailRequest(secondToken));
        assertThat(accountRepository.findByEmail(email).orElseThrow().isLoginAllowed()).isTrue();
    }

    @Test
    void resendForUnknownEmailStillAcknowledges() {
        var response = authService.resendVerificationEmail(
                new ResendVerificationRequest("nobody-" + System.nanoTime() + "@test.local"));
        assertThat(response.message()).contains("Nếu email tồn tại");
        verify(mailSender, org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
    }

    private String captureTokenFromLastMail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        Matcher matcher = TOKEN_IN_MAIL.matcher(message.getText());
        assertThat(matcher.find()).as("verification link in mail body").isTrue();
        return matcher.group(1);
    }
}
