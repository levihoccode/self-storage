package vn.lemar.selfstorage.notification.application;

import vn.lemar.selfstorage.notification.RentalRequestReceivedMail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    private static final RentalRequestReceivedMail MAIL = new RentalRequestReceivedMail(
            "john@example.com", "Nguyễn Văn A", "Self Storage Quận 7", "Medium",
            LocalDate.of(2026, 10, 1), 6, Instant.parse("2026-10-06T03:00:00Z"));

    @Mock
    JavaMailSender mailSender;

    @Test
    void sendsMailWithRequestDetails() {
        new EmailNotificationService(mailSender, "no-reply@test").sendRentalRequestReceived(MAIL);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("john@example.com");
        assertThat(sent.getValue().getFrom()).isEqualTo("no-reply@test");
        assertThat(sent.getValue().getText())
                .contains("Nguyễn Văn A", "Self Storage Quận 7", "Medium", "01/10/2026", "6 tháng", "10:00 06/10/2026");
    }

    @Test
    void swallowsMailFailure() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> new EmailNotificationService(mailSender, "no-reply@test")
                .sendRentalRequestReceived(MAIL)).doesNotThrowAnyException();
    }
}
