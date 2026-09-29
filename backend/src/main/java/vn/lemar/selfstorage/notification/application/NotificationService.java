package vn.lemar.selfstorage.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.lemar.selfstorage.notification.domain.Notification;
import vn.lemar.selfstorage.notification.repository.NotificationRepository;

/**
 * Thông báo web + email (A5).
 *
 * <p>Quy tắc: email lỗi chỉ được log — KHÔNG rollback việc lưu thông báo web.
 * Không import sang module khác; caller tự truyền {@code accountId} và {@code recipientEmail}.
 */
@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    public NotificationService(NotificationRepository notificationRepository, JavaMailSender mailSender) {
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    public Notification create(Long accountId, String recipientEmail, String type, String title, String body) {
        Notification notification = notificationRepository.save(new Notification(accountId, type, title, body));
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