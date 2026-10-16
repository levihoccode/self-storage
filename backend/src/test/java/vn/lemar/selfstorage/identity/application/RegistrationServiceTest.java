package vn.lemar.selfstorage.identity.application;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.identity.application.dto.RegisterRequest;
import vn.lemar.selfstorage.identity.application.dto.ResendVerificationRequest;
import vn.lemar.selfstorage.identity.application.dto.VerifyEmailRequest;
import vn.lemar.selfstorage.identity.application.exception.EmailAlreadyExistsException;
import vn.lemar.selfstorage.identity.application.exception.EmailAlreadyVerifiedException;
import vn.lemar.selfstorage.identity.application.exception.EmailNotRegisteredException;
import vn.lemar.selfstorage.identity.application.exception.InvalidVerificationCodeException;
import vn.lemar.selfstorage.identity.application.exception.VerificationResendTooEarlyException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.identity.repository.RoleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailVerificationCodes verificationCodes;
    @Mock
    private AuditRecorder auditRecorder;
    @Mock
    private ApplicationEventPublisher events;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(accountRepository, roleRepository, passwordEncoder,
                verificationCodes, auditRecorder, events, TTL);
    }

    @Test
    void registerCreatesCustomerAccountAuditsAndPublishesVerificationEmail() {
        Role customerRole = new Role("CUSTOMER");
        Account saved = mock(Account.class);
        when(saved.getId()).thenReturn(42L);
        when(accountRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(accountRepository.save(any(Account.class))).thenReturn(saved);
        when(verificationCodes.issue("customer@example.com")).thenReturn("123456");

        registrationService.register(new RegisterRequest(" Customer@Example.com ", "password123"));

        verify(accountRepository).save(argThat(account ->
                account.getEmail().equals("customer@example.com")
                        && account.getPasswordHash().equals("hash")
                        && account.getRole() == customerRole));
        verify(auditRecorder).record(new AuditEntry(AuditAction.ACCOUNT_REGISTERED, AuditEntityType.ACCOUNT,
                "42", null, null, null, 42L));
        verify(events).publishEvent(new VerificationEmailRequested("customer@example.com", "123456", TTL));
    }

    @Test
    void registerRejectsExistingEmail() {
        when(accountRepository.existsByEmail("customer@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("customer@example.com", "password123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verifyNoInteractions(auditRecorder, events, verificationCodes);
    }

    @Test
    void registerFailsWhenCustomerRoleIsNotSeeded() {
        when(accountRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("customer@example.com", "password123")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerMapsUniqueConstraintViolationToConflict() {
        when(accountRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER")));
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(accountRepository.save(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("customer@example.com", "password123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verifyNoInteractions(auditRecorder, events, verificationCodes);
    }

    @Test
    void verifyEmailMarksAccountVerified() {
        Account account = new Account("customer@example.com", "hash", new Role("CUSTOMER"));
        when(accountRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(account));

        EmailVerificationResult result =
                registrationService.verifyEmail(new VerifyEmailRequest("Customer@Example.com", "123456"));

        assertThat(result).isEqualTo(EmailVerificationResult.VERIFIED);
        assertThat(account.isVerified()).isTrue();
        verify(verificationCodes).verify("customer@example.com", "123456");
    }

    @Test
    void verifyEmailIsIdempotentForVerifiedAccount() {
        Account account = new Account("customer@example.com", "hash", new Role("CUSTOMER"));
        account.markEmailVerified();
        when(accountRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(account));

        EmailVerificationResult result =
                registrationService.verifyEmail(new VerifyEmailRequest("customer@example.com", "000000"));

        assertThat(result).isEqualTo(EmailVerificationResult.ALREADY_VERIFIED);
        verifyNoInteractions(verificationCodes);
    }

    @Test
    void verifyEmailRejectsUnknownAccountWithGenericCodeError() {
        when(accountRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.verifyEmail(
                new VerifyEmailRequest("ghost@example.com", "123456")))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    void resendIssuesNewCodeAfterCooldown() {
        Account account = new Account("customer@example.com", "hash", new Role("CUSTOMER"));
        when(accountRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(account));
        when(verificationCodes.issue("customer@example.com")).thenReturn("654321");

        registrationService.resendVerification(new ResendVerificationRequest("customer@example.com"));

        verify(verificationCodes).markResendCooldown("customer@example.com");
        verify(events).publishEvent(new VerificationEmailRequested("customer@example.com", "654321", TTL));
    }

    @Test
    void resendStopsAtCooldownViolation() {
        Account account = new Account("customer@example.com", "hash", new Role("CUSTOMER"));
        when(accountRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(account));
        doThrow(new VerificationResendTooEarlyException())
                .when(verificationCodes).markResendCooldown("customer@example.com");

        assertThatThrownBy(() -> registrationService.resendVerification(
                new ResendVerificationRequest("customer@example.com")))
                .isInstanceOf(VerificationResendTooEarlyException.class);
        verifyNoInteractions(events);
    }

    @Test
    void resendRejectsVerifiedAccount() {
        Account account = new Account("customer@example.com", "hash", new Role("CUSTOMER"));
        account.markEmailVerified();
        when(accountRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> registrationService.resendVerification(
                new ResendVerificationRequest("customer@example.com")))
                .isInstanceOf(EmailAlreadyVerifiedException.class);
        verifyNoInteractions(verificationCodes);
    }

    @Test
    void resendRejectsUnknownEmail() {
        when(accountRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.resendVerification(
                new ResendVerificationRequest("ghost@example.com")))
                .isInstanceOf(EmailNotRegisteredException.class);
    }
}
