package vn.lemar.selfstorage.notification;

/**
 * API công khai của module notification — các module khác chỉ gọi qua interface này.
 * Implementation gửi bất đồng bộ và không ném lỗi ra ngoài.
 */
public interface NotificationApi {

    /** Email xác nhận đã nhận yêu cầu đặt kho (Flow 1.1). */
    void sendRentalRequestReceived(RentalRequestReceivedMail mail);

    /**
     * Gửi 1 email tuỳ ý (to/subject/body) — dùng chung cho mọi module cần thông báo qua email
     * mà chưa có method riêng trong API này. Module gọi hàm này tự soạn nội dung; gửi bất đồng
     * bộ và không ném lỗi ra ngoài, giống các method khác trong API này.
     */
    void sendEmail(String to, String subject, String body);
}
