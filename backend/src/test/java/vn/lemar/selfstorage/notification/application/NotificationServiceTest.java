package vn.lemar.selfstorage.notification.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import vn.lemar.selfstorage.notification.domain.Notification;
import vn.lemar.selfstorage.notification.repository.NotificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createWebNotificationAndSendEmail() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.create(
                1L, "customer1@lemar.vn", "RENTAL_REQUEST_APPROVED", "Đơn được duyệt", "Khoang đã sẵn sàng");

        assertThat(result.getAccountId()).isEqualTo(1L);
        assertThat(result.getType()).isEqualTo("RENTAL_REQUEST_APPROVED");
        assertThat(result.getTitle()).isEqualTo("Đơn được duyệt");
        assertThat(result.getBody()).isEqualTo("Khoang đã sẵn sàng");
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getReadAt()).isNull();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void emailFailureDoesNotRollbackWebNotification() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        Notification result = notificationService.create(
                1L, "customer1@lemar.vn", "PAYMENT_SUCCEEDED", "Đã nhận cọc", "ok");

        assertThat(result.getType()).isEqualTo("PAYMENT_SUCCEEDED");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markAsRead() {
        Notification notification = new Notification(2L, "TYPE", "title", "body");

        notification.markRead();

        assertThat(notification.getReadAt()).isNotNull();
    }
}