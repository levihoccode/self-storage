package vn.lemar.selfstorage.notification;

/**
 * API công khai của module notification để gửi email. Module khác chỉ gọi qua interface này.
 *
 * <p>Lỗi gửi chỉ được log, không ném ra ngoài. Nếu caller đang trong transaction, email được
 * xếp lịch gửi **sau khi commit** — transaction rollback thì không gửi.
 *
 * <p><b>Known limitation</b> (spec: email chỉ gửi đi, không lưu — fail chỉ log):
 * <ul>
 *   <li>Best-effort, không retry/outbox: process chết sau commit nhưng trước khi gửi xong thì
 *       email mất, không dấu vết. Gửi lỗi trả {@code false} và không thử lại.</li>
 *   <li>Gửi đồng bộ trên thread đang gọi, kể cả nhánh afterCommit — SMTP chậm làm chậm caller.
 *       Chưa có {@code @Async}. Nếu cần, chỉ đẩy bước deliver sang executor, interface không đổi.</li>
 *   <li>Muốn bền (không mất mail, retry được) phải làm outbox + worker — cần đổi spec trước.</li>
 * </ul>
 */
public interface EmailSender {

    /**
     * Gửi email văn bản thuần.
     *
     * @return {@code true} nếu đã gửi hoặc đã xếp lịch sau commit — {@code false} nếu không gửi được
     */
    boolean send(String to, String subject, String body);

    /**
     * Gửi email nội dung HTML.
     *
     * <p>HTML do caller chịu trách nhiệm, không tự escape/sanitize — nên dựng nội dung qua
     * {@code NotificationEmailLayout.wrap(title, body)} nếu chỉ cần thông báo thường.
     *
     * @return {@code true} nếu đã gửi hoặc đã xếp lịch sau commit — {@code false} nếu không gửi được
     */
    boolean sendHtml(String to, String subject, String htmlBody);
}
