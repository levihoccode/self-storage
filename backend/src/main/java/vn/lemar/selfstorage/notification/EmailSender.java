package vn.lemar.selfstorage.notification;

/**
 * API công khai của module notification để gửi email. Module khác chỉ gọi qua interface này.
 */
public interface EmailSender {

    /**
     * Gửi một email văn bản thuần. Lỗi gửi chỉ được log, không ném ra ngoài để không rollback nghiệp vụ.
     *
     * @return {@code true} nếu mail server nhận thư, {@code false} nếu gửi thất bại
     */
    boolean send(String to, String subject, String body);
}
