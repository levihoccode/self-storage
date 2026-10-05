package vn.lemar.selfstorage.notification.application;

import java.util.Map;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * End-to-end cơ chế gửi sau commit: NotificationService chạy trong transaction thật trên Postgres,
 * JavaMailSender bị mock để chặn SMTP. Chỉ assert email có/không được gửi theo kết cục transaction.
 * Máy không có Docker thì test tự skip (giống SchemaValidationTest).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class NotificationEmailTransactionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void commitSendsEmailAfterCommit() {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        Map<String, Object> values = Map.of("orderCode", "O-1");

        new TransactionTemplate(transactionManager).execute(status ->
                notificationService.notify(6L, "customer1@lemar.vn", NotificationRecipient.CUSTOMER,
                        NotificationType.DEPOSIT_PAYMENT_SUCCEEDED, values, null));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void rollbackDoesNotSendEmail() {
        Map<String, Object> values = Map.of("orderCode", "O-1");

        new TransactionTemplate(transactionManager).execute(status -> {
            notificationService.notify(6L, "customer1@lemar.vn", NotificationRecipient.CUSTOMER,
                    NotificationType.DEPOSIT_PAYMENT_SUCCEEDED, values, null);
            status.setRollbackOnly();
            return null;
        });

        verifyNoInteractions(mailSender);
    }
}
