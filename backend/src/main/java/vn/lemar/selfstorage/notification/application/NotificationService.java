package vn.lemar.selfstorage.notification.application;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.lemar.selfstorage.notification.application.exception.NotificationNotFoundException;
import vn.lemar.selfstorage.notification.domain.Notification;
import vn.lemar.selfstorage.notification.domain.OrderNotification;
import vn.lemar.selfstorage.notification.repository.NotificationRepository;
import vn.lemar.selfstorage.notification.repository.OrderNotificationRepository;

/**
 * Thông báo web + email (A5).
 *
 * <p>Quy tắc: email lỗi chỉ được log — KHÔNG rollback việc lưu thông báo web.
 * Nội dung theo template hardcode của {@link NotificationTemplateCatalog}; riêng {@code OTHER}
 * tạo thủ công qua {@link #createOther}. Không import sang module khác; caller tự truyền
 * {@code accountId} và {@code recipientEmail}.
 */
@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final OrderNotificationRepository orderNotificationRepository;
    private final NotificationTemplateCatalog templateCatalog;
    private final JavaMailSender mailSender;

    public NotificationService(
            NotificationRepository notificationRepository,
            OrderNotificationRepository orderNotificationRepository,
            NotificationTemplateCatalog templateCatalog,
            JavaMailSender mailSender) {
        this.notificationRepository = notificationRepository;
        this.orderNotificationRepository = orderNotificationRepository;
        this.templateCatalog = templateCatalog;
        this.mailSender = mailSender;
    }

    /**
     * Tạo notification theo template của {@code type} và gửi email.
     *
     * @param orderId nếu khác null thì ghi thêm liên kết {@link OrderNotification}
     * @throws IllegalArgumentException thiếu giá trị placeholder hoặc type không có template
     */
    @Transactional
    public Notification notify(Long accountId, String recipientEmail, NotificationRecipient recipient,
            NotificationType type, Map<String, ?> values, Long orderId) {
        NotificationTemplateCatalog.Template template = templateCatalog.resolve(type, recipient);
        String title = template.renderTitle(values);
        String body = template.renderBody(values);
        return persist(accountId, type, title, body, recipientEmail, orderId);
    }

    /** Tạo notification {@code OTHER} với title/body thủ công — không dùng template. */
    @Transactional
    public Notification createOther(Long accountId, String recipientEmail, String title, String body) {
        return persist(accountId, NotificationType.OTHER, title, body, recipientEmail, null);
    }

    /** Danh sách notification của account, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<Notification> listForAccount(Long accountId, int page, int size) {
        return notificationRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(page, size))
                .getContent();
    }

    /** Notification thuộc đúng account — không thuộc thì coi như không tồn tại. */
    @Transactional(readOnly = true)
    public Notification getForAccount(Long accountId, Long notificationId) {
        return notificationRepository.findByIdAndAccountId(notificationId, accountId)
                .orElseThrow(NotificationNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long accountId) {
        return notificationRepository.countByAccountIdAndReadAtIsNull(accountId);
    }

    private Notification persist(Long accountId, NotificationType type, String title, String body,
            String recipientEmail, Long orderId) {
        Notification notification =
                notificationRepository.save(new Notification(accountId, type.name(), title, body));
        if (orderId != null) {
            orderNotificationRepository.save(new OrderNotification(orderId, notification.getId()));
        }
        sendEmailQuietly(recipientEmail, title, body);
        return notification;
    }

    private void sendEmailQuietly(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception e) {
            LOG.warn("Gửi email thất bại (không rollback thông báo web): to={}, subject={}, error={}",
                    to, subject, e.getMessage());
        }
    }
}
