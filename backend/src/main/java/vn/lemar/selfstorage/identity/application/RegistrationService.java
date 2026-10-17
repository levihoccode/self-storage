package vn.lemar.selfstorage.identity.application;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.domain.RoleName;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.identity.repository.RoleRepository;

/**
 * Đăng ký tài khoản khách hàng + vòng đời mã xác minh email (spec-gap #139): phát mã khi
 * đăng ký, xác minh, gửi lại mã. Gửi email đi qua event {@link VerificationEmailRequested}
 * (module notification lắng nghe) — gửi after-commit nên rollback không gửi.
 */
@Service
public class RegistrationService {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationCodes verificationCodes;
    private final AuditRecorder auditRecorder;
    private final ApplicationEventPublisher events;
    private final Duration codeTtl;

    public RegistrationService(
            AccountRepository accountRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationCodes verificationCodes,
            AuditRecorder auditRecorder,
            ApplicationEventPublisher events,
            @Value("${security.email-verification.ttl:PT10M}") Duration codeTtl) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodes = verificationCodes;
        this.auditRecorder = auditRecorder;
        this.events = events;
        this.codeTtl = codeTtl;
    }

    /** Tạo account CUSTOMER (chưa xác minh) + audit ACCOUNT_REGISTERED + gửi mã xác minh. */
    @Transactional
    public void register(RegisterRequest request) {
        String email = Account.normalize(request.email());
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER.name())
                .orElseThrow(() -> new IllegalStateException("Role CUSTOMER chưa được seed"));
        Account account;
        try {
            account = accountRepository.save(
                    new Account(email, passwordEncoder.encode(request.password()), customerRole));
        } catch (DataIntegrityViolationException e) {
            // Hai request đăng ký cùng email chạy song song — unique index bắt được.
            throw new EmailAlreadyExistsException();
        }
        auditRecorder.record(new AuditEntry(AuditAction.ACCOUNT_REGISTERED, AuditEntityType.ACCOUNT,
                String.valueOf(account.getId()), null, null, null, account.getId()));
        issueVerificationCode(email);
    }

    /** Xác minh email; account đã xác minh thì no-op (idempotent, không lỗi, không kiểm mã). */
    @Transactional
    public EmailVerificationResult verifyEmail(VerifyEmailRequest request) {
        Account account = accountRepository.findByEmail(Account.normalize(request.email()))
                .orElseThrow(InvalidVerificationCodeException::new);
        if (account.isVerified()) {
            return EmailVerificationResult.ALREADY_VERIFIED;
        }
        verificationCodes.verify(account.getEmail(), request.code());
        account.markEmailVerified();
        events.publishEvent(new EmailVerified(account.getId()));
        return EmailVerificationResult.VERIFIED;
    }

    /** Gửi lại mã xác minh; cooldown chống spam cấu hình ở {@link EmailVerificationCodes}. */
    @Transactional(readOnly = true)
    public void resendVerification(ResendVerificationRequest request) {
        Account account = accountRepository.findByEmail(Account.normalize(request.email()))
                .orElseThrow(EmailNotRegisteredException::new);
        if (account.isVerified()) {
            throw new EmailAlreadyVerifiedException();
        }
        verificationCodes.markResendCooldown(account.getEmail());
        issueVerificationCode(account.getEmail());
    }

    private void issueVerificationCode(String email) {
        String code = verificationCodes.issue(email);
        events.publishEvent(new VerificationEmailRequested(email, code, codeTtl));
    }
}
