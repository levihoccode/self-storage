package vn.lemar.selfstorage.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import vn.lemar.selfstorage.notification.EmailSender;

@Service
class SmtpEmailSender implements EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            LOG.warn("Bỏ qua gửi email: thiếu địa chỉ người nhận (subject={})", subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            LOG.error("Gửi email thất bại (to={}, subject={})", to, subject, e);
            return false;
        }
    }
}
