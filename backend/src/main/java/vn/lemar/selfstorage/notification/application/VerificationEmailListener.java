package vn.lemar.selfstorage.notification.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.lemar.selfstorage.identity.application.VerificationEmailRequested;
import vn.lemar.selfstorage.notification.EmailSender;

/**
 * Gửi email chứa mã xác minh khi identity phát {@link VerificationEmailRequested}.
 *
 * <p>Listener chạy đồng bộ trong transaction của identity, nhưng {@link EmailSender} tự xếp
 * lịch gửi sau commit — transaction rollback thì email không đi. Module identity không phụ
 * thuộc notification (cycle), nên đây là điểm nối duy nhất giữa hai module cho luồng đăng ký.
 */
@Component
class VerificationEmailListener {

    private final EmailSender emailSender;

    VerificationEmailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @EventListener
    void onVerificationEmailRequested(VerificationEmailRequested event) {
        String title = "Mã xác minh email đăng ký Kho Mộc";
        String body = """
                Mã xác minh email của bạn là: %s

                Mã có hiệu lực trong %d phút. Nếu bạn không đăng ký tài khoản, hãy bỏ qua email này."""
                .formatted(event.code(), event.validFor().toMinutes());
        emailSender.sendHtml(event.email(), title, NotificationEmailLayout.wrap(title, body));
    }
}
