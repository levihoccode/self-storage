package vn.lemar.selfstorage.payment.application;

import java.util.Collection;

/**
 * Cho module nghiệp vụ hỏi "hóa đơn này có phiên thanh toán đang mở không" mà không phải chạm
 * vào bảng của payment.
 *
 * <p>Spec Flow 1.4 chặn đặt cọc khi khoang "đang có giao dịch khác xử lý (chưa bị timeout)".
 * Khóa ở đây suy ra từ dữ liệu sẵn có chứ không phải khóa phân tán: một giao dịch còn
 * {@code Pending} và tạo chưa quá {@link PaymentSession#WINDOW} nghĩa là phiên còn mở. Cách này
 * tự hết hạn, sống sót qua restart và không cần bảng mới.
 */
public interface PaymentSessionQuery {

    /** Có ít nhất một phiên còn mở trong nhóm hóa đơn này không. Danh sách rỗng trả {@code false}. */
    boolean hasOpenSession(Collection<Long> invoiceIds);
}
