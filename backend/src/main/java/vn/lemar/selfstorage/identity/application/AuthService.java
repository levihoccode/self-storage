package vn.lemar.selfstorage.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.identity.application.dto.LoginRequest;
import vn.lemar.selfstorage.identity.application.dto.LoginResponse;
import vn.lemar.selfstorage.identity.application.dto.RegisterRequest;
import vn.lemar.selfstorage.identity.application.dto.RegisterResponse;
import vn.lemar.selfstorage.identity.application.dto.ResendVerificationRequest;
import vn.lemar.selfstorage.identity.application.dto.ResendVerificationResponse;
import vn.lemar.selfstorage.identity.application.dto.VerifyEmailRequest;
import vn.lemar.selfstorage.identity.application.exception.AccountNotAllowedException;
import vn.lemar.selfstorage.identity.application.exception.EmailAlreadyExistsException;
import vn.lemar.selfstorage.identity.application.exception.InvalidCredentialsException;
import vn.lemar.selfstorage.identity.application.exception.InvalidOrExpiredTokenException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.EmailVerificationToken;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.domain.RoleName;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.identity.repository.EmailVerificationTokenRepository;
import vn.lemar.selfstorage.identity.repository.RoleRepository;

@Service
public class AuthService {

    private static final int TOKEN_BYTES = 32;
    private static final long TOKEN_TTL_HOURS = 24;

    private static final String RESEND_ACK_MESSAGE =
            "Nếu email tồn tại và chưa xác minh, bạn sẽ nhận được email xác thực.";

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String verifyBaseUrl;
    private final String mailFrom;

    public AuthService(AccountRepository accountRepository,
                       RoleRepository roleRepository,
                       EmailVerificationTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JavaMailSender mailSender,
                       @Value("${app.verify-base-url}") String verifyBaseUrl,
                       @Value("${app.mail.from}") String mailFrom) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailSender = mailSender;
        this.verifyBaseUrl = verifyBaseUrl;
        this.mailFrom = mailFrom;
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

        String accessToken = jwtService.generateAccessToken(account);

        return new LoginResponse(
                account.getId(),
                account.getEmail(),
                account.getRole().getName(),
                accessToken,
                jwtService.getAccessTokenExpirationSeconds()
        );
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = Account.normalize(request.email());
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        Role role = roleRepository.findByName(RoleName.CUSTOMER.name()).orElseThrow();
        Account account = new Account(email, passwordEncoder.encode(request.password()), role);
        accountRepository.save(account);

        issueVerificationToken(account);
        return new RegisterResponse(
                account.getId(),
                account.getEmail(),
                "Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.");
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        EmailVerificationToken token = tokenRepository
                .findByTokenHash(sha256Hex(request.token()))
                .orElseThrow(InvalidOrExpiredTokenException::new);

        if (token.isConsumed() || token.isExpired()) {
            throw new InvalidOrExpiredTokenException();
        }

        token.markConsumed();
        token.getAccount().markEmailVerified();
    }

    @Transactional
    public ResendVerificationResponse resendVerificationEmail(ResendVerificationRequest request) {
        String email = Account.normalize(request.email());
        accountRepository.findByEmail(email).ifPresent(account -> {
            if (account.isLoginAllowed()) {
                return;
            }
            tokenRepository.findByAccountIdAndConsumedAtIsNull(account.getId())
                    .ifPresent(old -> {
                        old.markConsumed();
                        tokenRepository.saveAndFlush(old);
                    });
            issueVerificationToken(account);
        });
        return new ResendVerificationResponse(RESEND_ACK_MESSAGE);
    }

    private void issueVerificationToken(Account account) {
        String rawToken = generateRawToken();
        tokenRepository.save(new EmailVerificationToken(
                account, sha256Hex(rawToken), Instant.now().plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS)));
        sendVerificationMail(account.getEmail(), rawToken);
    }

    private void sendVerificationMail(String to, String rawToken) {
        String verifyLink = verifyBaseUrl + "?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("Xác thực tài khoản Self Storage");
        message.setText("""
                Chào bạn,

                Vui lòng bấm vào link sau để xác thực email (hiệu lực 24 giờ):
                %s

                Nếu bạn không thực hiện đăng ký này, hãy bỏ qua email này.
                """.formatted(verifyLink));
        mailSender.send(message);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
