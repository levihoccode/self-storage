package vn.lemar.selfstorage.notification.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import vn.lemar.selfstorage.notification.domain.Notification;
import vn.lemar.selfstorage.notification.domain.OrderNotification;
import vn.lemar.selfstorage.notification.repository.NotificationRepository;
import vn.lemar.selfstorage.notification.repository.OrderNotificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private OrderNotificationRepository orderNotificationRepository;

    @Mock
    private JavaMailSender mailSender;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, orderNotificationRepository, new NotificationTemplateCatalog(), mailSender);
    }

    private void stubSaveAssignsId(long id) {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", id);
            return notification;
        });
    }

    @Test
    void notifyRendersTemplateAndSendsEmail() {
        stubSaveAssignsId(1L);
        Map<String, Object> values = Map.of("unitCode", "A-01", "proposalLink", "https://lemar.vn/p/1");

        Notification result = notificationService.notify(1L, "customer1@lemar.vn", NotificationRecipient.CUSTOMER,
                NotificationType.RENTAL_REQUEST_APPROVED, values, null);

        assertThat(result.getType()).isEqualTo("RENTAL_REQUEST_APPROVED");
        assertThat(result.getTitle()).isEqualTo("Yêu cầu thuê kho đã được duyệt");
        assertThat(result.getBody()).contains("A-01").contains("https://lemar.vn/p/1");
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getReadAt()).isNull();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void notifyRendersRecipientSpecificTemplate() {
        stubSaveAssignsId(2L);
        Map<String, Object> values = Map.of(
                "orderCode", "O-1", "appointmentTime", "10:00 06/10", "facilityAddress", "Q1");

        Notification fmNotification = notificationService.notify(2L, "fm@lemar.vn", NotificationRecipient.FM,
                NotificationType.APPOINTMENT_CREATED, values, null);
        Notification customerNotification = notificationService.notify(3L, "customer@lemar.vn",
                NotificationRecipient.CUSTOMER, NotificationType.APPOINTMENT_CREATED, values, null);

        assertThat(fmNotification.getTitle()).isEqualTo("Có lịch check-in mới cần phân công");
        assertThat(customerNotification.getTitle()).isEqualTo("Lịch hẹn check-in đã được tạo");
    }

    @Test
    void notifyRejectsMissingPlaceholder() {
        assertThatThrownBy(() -> notificationService.notify(1L, "customer1@lemar.vn",
                NotificationRecipient.CUSTOMER, NotificationType.RENTAL_REQUEST_APPROVED,
                Map.of("unitCode", "A-01"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proposalLink");

        verifyNoInteractions(notificationRepository, orderNotificationRepository, mailSender);
    }

    @Test
    void notifyRejectsRecipientOutsideCatalog() {
        Map<String, Object> values = Map.of("unitCode", "A-01", "reason", "hết khoang");

        assertThatThrownBy(() -> notificationService.notify(1L, "customer1@lemar.vn",
                NotificationRecipient.CUSTOMER, NotificationType.PROPOSAL_REJECTED, values, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROPOSAL_REJECTED/CUSTOMER");

        verifyNoInteractions(notificationRepository, orderNotificationRepository, mailSender);
    }

    @Test
    void notifyCreatesOrderNotificationLink() {
        stubSaveAssignsId(10L);
        Map<String, Object> values = Map.of("orderCode", "O-1", "expiresAt", "2026-11-01", "daysLeft", 3);

        notificationService.notify(1L, "customer1@lemar.vn", NotificationRecipient.CUSTOMER,
                NotificationType.RENTAL_ORDER_EXPIRING_SOON, values, 7L);

        ArgumentCaptor<OrderNotification> captor = ArgumentCaptor.forClass(OrderNotification.class);
        verify(orderNotificationRepository).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(7L);
        assertThat(captor.getValue().getNotificationId()).isEqualTo(10L);
    }

    @Test
    void createOtherStoresManualContent() {
        stubSaveAssignsId(11L);

        Notification result = notificationService.createOther(1L, "customer1@lemar.vn",
                "Bảo trì khẩn cấp", "Cơ sở tạm đóng ngày mai");

        assertThat(result.getType()).isEqualTo("OTHER");
        assertThat(result.getTitle()).isEqualTo("Bảo trì khẩn cấp");
        verifyNoInteractions(orderNotificationRepository);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void emailFailureDoesNotRollbackWebNotification() {
        stubSaveAssignsId(1L);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        Notification result = notificationService.notify(1L, "customer1@lemar.vn", NotificationRecipient.CUSTOMER,
                NotificationType.DEPOSIT_PAYMENT_SUCCEEDED, Map.of("orderCode", "O-1"), null);

        assertThat(result.getType()).isEqualTo("DEPOSIT_PAYMENT_SUCCEEDED");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markReadSetsReadAt() {
        Notification notification = new Notification(1L, "TYPE", "Tiêu đề", "Nội dung");
        when(notificationRepository.findByIdAndAccountId(10L, 1L)).thenReturn(Optional.of(notification));

        Notification result = notificationService.markRead(1L, 10L);

        assertThat(result.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markReadIsIdempotent() {
        Notification notification = new Notification(1L, "TYPE", "Tiêu đề", "Nội dung");
        notification.markRead();
        when(notificationRepository.findByIdAndAccountId(10L, 1L)).thenReturn(Optional.of(notification));

        notificationService.markRead(1L, 10L);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markAllReadUpdatesUnreadOnly() {
        Notification first = new Notification(1L, "TYPE", "A", "a");
        Notification second = new Notification(1L, "TYPE", "B", "b");
        when(notificationRepository.findByAccountIdAndReadAtIsNull(1L)).thenReturn(List.of(first, second));

        int marked = notificationService.markAllRead(1L);

        assertThat(marked).isEqualTo(2);
        assertThat(first.getReadAt()).isNotNull();
        assertThat(second.getReadAt()).isNotNull();
        verify(notificationRepository).saveAll(List.of(first, second));
    }

    @Test
    void listForAccountFiltersByReadState() {
        when(notificationRepository.findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(Page.empty());

        notificationService.listForAccount(1L, false, 0, 20);

        verify(notificationRepository).findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(eq(1L), any());
        verify(notificationRepository, never()).findByAccountIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void markAsRead() {
        Notification notification = new Notification(2L, "TYPE", "title", "body");

        notification.markRead();

        assertThat(notification.getReadAt()).isNotNull();
    }
}
