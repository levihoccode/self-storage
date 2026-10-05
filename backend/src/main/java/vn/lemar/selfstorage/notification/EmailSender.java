package vn.lemar.selfstorage.notification;

/**
 * API công khai của module notification để gửi email. Module khác chỉ gọi qua interface này.
 *
 * <p>Lỗi gửi chỉ được log, không ném ra ngoài. Nếu caller đang trong transaction, email được
 * xếp lịch gửi **sau khi commit** — transaction rollback thì không gửi.
 */
public interface EmailSender {

    /**
     * Gửi email văn bản thuần.
     *
     * @return {@code true} nếu đã gửi hoặc đã xếp lịch sau commit; {@code false} nếu không gửi được
     */
    boolean send(String to, String subject, String body);

    /**
     * Gửi email nội dung HTML.
     *
     * @return {@code true} nếu đã gửi hoặc đã xếp lịch sau commit; {@code false} nếu không gửi được
     */
    boolean sendHtml(String to, String subject, String htmlBody);
}
