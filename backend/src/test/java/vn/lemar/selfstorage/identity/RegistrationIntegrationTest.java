package vn.lemar.selfstorage.identity;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import vn.lemar.selfstorage.identity.application.VerificationEmailRequested;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đăng ký + xác minh email + rate limit login chạy thật trên Postgres + Redis (Testcontainers).
 * SMTP bị mock; mã xác minh lấy từ event {@link VerificationEmailRequested} (mã plaintext không lưu
 * ở đâu khác). Class KHÔNG đánh {@code @Transactional} — cần commit thật để email after-commit đi
 * và để rollback semantics đúng như production. Máy không có Docker thì test tự skip.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@RecordApplicationEvents
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.email-verification.resend-cooldown=PT1S"
})
class RegistrationIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String WRONG_CODE = "000000";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void mockMimeMessage() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void registerCreatesUnverifiedCustomerAuditsAndSendsHashedCode() throws Exception {
        String email = uniqueEmail("reg");

        register(email).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng ký thành công. Kiểm tra email để lấy mã xác minh."));

        Account account = accountRepository.findWithRoleByEmail(email).orElseThrow();
        assertThat(account.isVerified()).isFalse();
        assertThat(account.getRole().getName()).isEqualTo("CUSTOMER");
        Integer audits = jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'ACCOUNT_REGISTERED' and entity_id = ?",
                Integer.class, String.valueOf(account.getId()));
        assertThat(audits).isEqualTo(1);

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));

        String code = lastCodeFor(email);
        assertThat(code).matches("\\d{6}");
        String codeKey = "auth:verify-code:" + email;
        Object storedHash = redisTemplate.opsForHash().get(codeKey, "hash");
        assertThat(storedHash).isNotNull().isNotEqualTo(code);
        assertThat(redisTemplate.getExpire(codeKey)).isPositive();
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail("dup");

        register(email).andExpect(status().isOk());
        register(email).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email đã được đăng ký"));
    }

    @Test
    void verifyEmailActivatesAccountAndIsIdempotent() throws Exception {
        String email = uniqueEmail("ver");
        register(email).andExpect(status().isOk());
        String code = lastCodeFor(email);

        verifyEmail(email, code).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xác minh email thành công"));

        assertThat(accountRepository.findByEmail(email).orElseThrow().isVerified()).isTrue();
        assertThat(redisTemplate.hasKey("auth:verify-code:" + email)).isFalse();

        verifyEmail(email, code).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email đã được xác minh trước đó"));
    }

    @Test
    void verifyEmailLocksAfterFiveWrongAttempts() throws Exception {
        String email = uniqueEmail("lock");
        register(email).andExpect(status().isOk());
        String code = lastCodeFor(email);
        String wrongCode = WRONG_CODE.equals(code) ? "000001" : WRONG_CODE;

        for (int attempt = 1; attempt <= 4; attempt++) {
            verifyEmail(email, wrongCode).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Mã xác minh không đúng hoặc đã hết hạn"));
        }
        verifyEmail(email, wrongCode).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Bạn đã nhập sai quá nhiều lần. Vui lòng gửi lại mã mới."));

        verifyEmail(email, code).andExpect(status().isTooManyRequests());
        assertThat(accountRepository.findByEmail(email).orElseThrow().isVerified()).isFalse();
    }

    @Test
    void verifyEmailRejectsExpiredCode() throws Exception {
        String email = uniqueEmail("exp");
        register(email).andExpect(status().isOk());
        String code = lastCodeFor(email);

        redisTemplate.delete("auth:verify-code:" + email);

        verifyEmail(email, code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã xác minh không đúng hoặc đã hết hạn"));
    }

    @Test
    void resendIssuesNewCodeAfterCooldownAndRejectsVerifiedAccount() throws Exception {
        String email = uniqueEmail("resend");
        register(email).andExpect(status().isOk());

        // Resend đầu tiên sau register được phép — cooldown chỉ áp giữa hai lần resend.
        resend(email).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã gửi lại mã xác minh"));
        resend(email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Vui lòng chờ trước khi yêu cầu gửi lại mã."));

        Thread.sleep(1100);
        resend(email).andExpect(status().isOk());
        assertThat(eventsFor(email, VerificationEmailRequested.class)).hasSize(3);

        verifyEmail(email, lastCodeFor(email)).andExpect(status().isOk());
        resend(email).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tài khoản đã được xác minh"));
    }

    @Test
    void resendRejectsUnknownEmail() throws Exception {
        resend("ghost-" + UUID.randomUUID() + "@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email chưa được đăng ký"));
    }

    @Test
    void loginBlocksAfterFiveFailuresEvenWithCorrectPassword() throws Exception {
        String email = uniqueEmail("brute");
        register(email).andExpect(status().isOk());
        verifyEmail(email, lastCodeFor(email)).andExpect(status().isOk());

        for (int attempt = 1; attempt <= 5; attempt++) {
            login(email, "sai-mat-khau").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Quá nhiều lần thử. Vui lòng thử lại sau."));
    }

    @Test
    void loginIsBlockedUntilEmailVerified() throws Exception {
        String email = uniqueEmail("gate");
        register(email).andExpect(status().isOk());

        login(email, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản chưa xác minh email"));

        verifyEmail(email, lastCodeFor(email)).andExpect(status().isOk());

        login(email, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void loginResetsEmailCounterAfterSuccess() throws Exception {
        String email = "customer1@lemar.vn";

        login(email, "sai-mat-khau").andExpect(status().isUnauthorized());
        login(email, "sai-mat-khau").andExpect(status().isUnauthorized());
        login(email, "Test@1234").andExpect(status().isOk());

        String emailKey = "auth:login-fail:email:" + email;
        assertThat(redisTemplate.opsForValue().get(emailKey)).isNull();

        login(email, "sai-mat-khau").andExpect(status().isUnauthorized());
        assertThat(redisTemplate.opsForValue().get(emailKey)).isEqualTo("1");
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private ResultActions verifyEmail(String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private <T> List<T> eventsFor(String email, Class<T> type) {
        return applicationEvents.stream(type)
                .filter(event -> event instanceof VerificationEmailRequested requested
                        && requested.email().equals(email))
                .toList();
    }

    private String lastCodeFor(String email) {
        List<VerificationEmailRequested> events = eventsFor(email, VerificationEmailRequested.class);
        assertThat(events).as("phải có event gửi mã cho %s", email).isNotEmpty();
        return events.get(events.size() - 1).code();
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
