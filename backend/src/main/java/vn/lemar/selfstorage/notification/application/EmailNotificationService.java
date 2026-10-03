package vn.lemar.selfstorage.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.lemar.selfstorage.notification.NotificationApi;
import vn.lemar.selfstorage.notification.RentalRequestReceivedMail;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * API công khai của module notification cho việc gửi email.
 *
 * <p>Gửi bất đồng bộ và nuốt mọi lỗi (chỉ LOG): lỗi email không được làm hỏng giao dịch
 * nghiệp vụ (backend/AGENTS.md — Notifications). Chưa có outbox/retry theo specs/draft.md.
 */
@Service
public class EmailNotificationService implements NotificationApi {

    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationService.class);
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationService(JavaMailSender mailSender,
                                    @Value("${app.mail.from:no-reply@selfstorage.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    @Async
    public void sendRentalRequestReceived(RentalRequestReceivedMail mail) {
        sendEmail(mail.toEmail(), "Đã nhận yêu cầu thuê kho của bạn", buildRentalRequestReceivedBody(mail));
    }

    @Override
    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (RuntimeException ex) {
            LOG.warn("Không gửi được email tới {}", to, ex);
        }
    }

    static String buildRentalRequestReceivedBody(RentalRequestReceivedMail mail) {
        return "Chào " + mail.customerName() + ",\n\n"
                + "Chúng tôi đã nhận được yêu cầu thuê kho của bạn với thông tin:\n"
                + "  - Điểm kho: " + mail.facilityName() + "\n"
                + "  - Loại khoang: " + mail.unitTypeName() + "\n"
                + "  - Ngày bắt đầu: " + DATE.format(mail.startDate()) + "\n"
                + "  - Thời hạn thuê: " + mail.periodMonths() + " tháng\n\n"
                + "Nhân viên sẽ xem xét và liên hệ xác nhận qua email và số điện thoại của bạn. "
                + "Yêu cầu có hiệu lực đến " + DATE_TIME.format(mail.expiresAt().atZone(DISPLAY_ZONE)) + ".\n\n"
                + "Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email.\n";
    }
}
