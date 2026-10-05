package vn.lemar.selfstorage.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpEmailSender sender = new SmtpEmailSender(mailSender, "noreply@lemar.vn");

    @Test
    void sendsMessageWithGivenFields() {
        boolean result = sender.send("a@b.vn", "Tiêu đề", "Nội dung");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(result).isTrue();
        assertThat(sent.getFrom()).isEqualTo("noreply@lemar.vn");
        assertThat(sent.getTo()).containsExactly("a@b.vn");
        assertThat(sent.getSubject()).isEqualTo("Tiêu đề");
        assertThat(sent.getText()).isEqualTo("Nội dung");
    }

    @Test
    void swallowsMailFailureAndReturnsFalse() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(sender.send("a@b.vn", "s", "b")).isFalse();
    }

    @Test
    void skipsBlankRecipient() {
        assertThat(sender.send(" ", "s", "b")).isFalse();
        assertThat(sender.send(null, "s", "b")).isFalse();
        verifyNoInteractions(mailSender);
    }
}
