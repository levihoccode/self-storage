package vn.lemar.selfstorage.identity.application;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String EMAIL = "customer@example.com";

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

    // ------------------------------------------------------------ register: chỉ ghi pending

    @Test
    void registerStoresPendingAndPublishesVerificationEmail() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("password-hash");
        when(verificationCodes.issue(EMAIL, "password-hash")).thenReturn("123456");

        registrationService.register(new RegisterRequest(" Customer@Example.com ", "password123"));

        verify(verificationCodes).markResendCooldown(EMAIL);
        verify(verificationCodes).issue(EMAIL, "password-hash");
        verify(events).publishEvent(new VerificationEmailRequested(EMAIL, "123456", TTL));
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(roleRepository, auditRecorder);
    }

    @Test
    void registerRejectsExistingEmail() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(new RegisterRequest(EMAIL, "password123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verifyNoInteractions(verificationCodes, events, auditRecorder);
    }

    @Test
    void registerStopsAtCooldownViolation() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        doThrow(new VerificationResendTooEarlyException())
                .when(verificationCodes).markResendCooldown(EMAIL);

        assertThatThrownBy(() -> registrationService.register(new RegisterRequest(EMAIL, "password123")))
                .isInstanceOf(VerificationResendTooEarlyException.class);
        verifyNoInteractions(events, auditRecorder);
    }

    // ------------------------------------------------------------ verify: tạo account từ pending

    @Test
    void verifyCreatesVerifiedCustomerAccountFromPending() {
        Role customerRole = new Role("CUSTOMER");
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(verificationCodes.verify(EMAIL, "123456"))
                .thenReturn(Optional.of(new PendingRegistration(EMAIL, "password-hash")));
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        EmailVerificationResult result =
                registrationService.verifyEmail(new VerifyEmailRequest(" Customer@Example.com ", "123456"));

        assertThat(result).isEqualTo(EmailVerificationResult.VERIFIED);
        ArgumentCaptor<Account> savedAccount = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(savedAccount.capture());
        assertThat(savedAccount.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(savedAccount.getValue().getPasswordHash()).isEqualTo("password-hash");
        assertThat(savedAccount.getValue().getRole()).isEqualTo(customerRole);
        assertThat(savedAccount.getValue().isVerified()).isTrue();
        verify(auditRecorder).record(new AuditEntry(AuditAction.ACCOUNT_REGISTERED, AuditEntityType.ACCOUNT,
                "42", null, null, null, 42L));
    }

    @Test
    void verifyIsIdempotentForVerifiedAccount() {
        Account account = new Account(EMAIL, "hash", new Role("CUSTOMER"));
        account.markEmailVerified();
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));

        EmailVerificationResult result =
                registrationService.verifyEmail(new VerifyEmailRequest(EMAIL, "000000"));

        assertThat(result).isEqualTo(EmailVerificationResult.ALREADY_VERIFIED);
        verifyNoInteractions(verificationCodes, auditRecorder, events);
    }

    @Test
    void verifyLegacyUnverifiedAccountJustMarksVerified() {
        Account account = new Account(EMAIL, "hash", new Role("CUSTOMER"));
        ReflectionTestUtils.setField(account, "id", 7L);
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(verificationCodes.verify(EMAIL, "123456")).thenReturn(Optional.empty());

        EmailVerificationResult result =
                registrationService.verifyEmail(new VerifyEmailRequest(EMAIL, "123456"));

        assertThat(result).isEqualTo(EmailVerificationResult.VERIFIED);
        assertThat(account.isVerified()).isTrue();
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    void verifyRejectsUnknownEmailWithoutPending() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(verificationCodes.verify(EMAIL, "123456")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.verifyEmail(new VerifyEmailRequest(EMAIL, "123456")))
                .isInstanceOf(InvalidVerificationCodeException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void verifyFailsWhenCustomerRoleIsMissing() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(verificationCodes.verify(EMAIL, "123456"))
                .thenReturn(Optional.of(new PendingRegistration(EMAIL, "password-hash")));
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.verifyEmail(new VerifyEmailRequest(EMAIL, "123456")))
                .isInstanceOf(IllegalStateException.class);
        verify(accountRepository, never()).save(any());
        verify(verificationCodes, never()).consume(EMAIL);
        verifyNoInteractions(auditRecorder, events);
    }

    @Test
    void verifyConsumesCodeOnlyAfterCommit() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(verificationCodes.verify(EMAIL, "123456"))
                .thenReturn(Optional.of(new PendingRegistration(EMAIL, "password-hash")));
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER")));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            registrationService.verifyEmail(new VerifyEmailRequest(EMAIL, "123456"));
            verify(verificationCodes, never()).consume(EMAIL);

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(verificationCodes).consume(EMAIL);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void verifyMapsConcurrentAccountCreationToAlreadyVerified() {
        Account verified = new Account(EMAIL, "hash", new Role("CUSTOMER"));
        verified.markEmailVerified();
        when(accountRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(verified));
        when(verificationCodes.verify(EMAIL, "123456"))
                .thenReturn(Optional.of(new PendingRegistration(EMAIL, "password-hash")));
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER")));
        when(accountRepository.save(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        EmailVerificationResult result =
                registrationService.verifyEmail(new VerifyEmailRequest(EMAIL, "123456"));

        assertThat(result).isEqualTo(EmailVerificationResult.ALREADY_VERIFIED);
        verify(verificationCodes, never()).consume(EMAIL);
        verifyNoInteractions(auditRecorder);
    }

    @Test
    void verifyPublishesEmailVerifiedOnlyOnFirstVerification() {
        Account created = new Account(EMAIL, "password-hash", new Role("CUSTOMER"));
        created.markEmailVerified();
        ReflectionTestUtils.setField(created, "id", 42L);
        when(accountRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));
        when(verificationCodes.verify(EMAIL, "123456"))
                .thenReturn(Optional.of(new PendingRegistration(EMAIL, "password-hash")));
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER")));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        registrationService.verifyEmail(new VerifyEmailRequest(EMAIL, "123456"));
        registrationService.verifyEmail(new VerifyEmailRequest(EMAIL, "123456"));

        verify(events, times(1)).publishEvent(new EmailVerified(42L));
    }

    // ------------------------------------------------------------ resend

    @Test
    void resendReissuesCodeForPendingRegistration() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(verificationCodes.hasPending(EMAIL)).thenReturn(true);
        when(verificationCodes.reissue(EMAIL)).thenReturn("654321");

        registrationService.resendVerification(new ResendVerificationRequest(EMAIL));

        verify(verificationCodes).markResendCooldown(EMAIL);
        verify(events).publishEvent(new VerificationEmailRequested(EMAIL, "654321", TTL));
    }

    @Test
    void resendStopsAtCooldownViolation() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(verificationCodes.hasPending(EMAIL)).thenReturn(true);
        doThrow(new VerificationResendTooEarlyException())
                .when(verificationCodes).markResendCooldown(EMAIL);

        assertThatThrownBy(() -> registrationService.resendVerification(new ResendVerificationRequest(EMAIL)))
                .isInstanceOf(VerificationResendTooEarlyException.class);
        verify(verificationCodes, never()).reissue(EMAIL);
        verifyNoInteractions(events);
    }

    @Test
    void resendRejectsVerifiedAccount() {
        Account account = new Account(EMAIL, "hash", new Role("CUSTOMER"));
        account.markEmailVerified();
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> registrationService.resendVerification(new ResendVerificationRequest(EMAIL)))
                .isInstanceOf(EmailAlreadyVerifiedException.class);
        verifyNoInteractions(verificationCodes);
    }

    @Test
    void resendRejectsUnknownEmailWithoutPending() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(verificationCodes.hasPending(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> registrationService.resendVerification(new ResendVerificationRequest(EMAIL)))
                .isInstanceOf(EmailNotRegisteredException.class);
        verify(verificationCodes, never()).markResendCooldown(EMAIL);
        verifyNoInteractions(events);
    }

    @Test
    void resendLegacyUnverifiedAccountReissuesCode() {
        Account account = new Account(EMAIL, "hash", new Role("CUSTOMER"));
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(verificationCodes.reissue(EMAIL)).thenReturn("654321");

        registrationService.resendVerification(new ResendVerificationRequest(EMAIL));

        verify(verificationCodes).markResendCooldown(EMAIL);
        verify(events).publishEvent(new VerificationEmailRequested(EMAIL, "654321", TTL));
    }
}
