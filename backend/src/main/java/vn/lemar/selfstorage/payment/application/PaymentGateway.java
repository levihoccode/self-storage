package vn.lemar.selfstorage.payment.application;

import java.time.Instant;
import java.util.Map;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.application.dto.TransactionQuery;
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult;

/**
 * Cổng thanh toán, tách khỏi nghiệp vụ để đổi nhà cung cấp không phải sửa flow nào.
 *
 * <p>Adapter chịu trách nhiệm: sinh mã giao dịch đúng định dạng của cổng, dựng URL, xác thực
 * callback, truy vấn trạng thái và chuẩn hoá kết quả — nghiệp vụ không đọc mã riêng của cổng.
 */
public interface PaymentGateway {

    /** Tên provider dùng cho log/cấu hình, ví dụ {@code vnpay}, {@code zalopay}. */
    String provider();

    /**
     * Chuyển mã tham chiếu nội bộ sang mã cổng yêu cầu. Mặc định giữ nguyên.
     *
     * <p>ZaloPay bắt buộc {@code app_trans_id} bắt đầu bằng {@code yyMMdd} (GMT+7), nên adapter
     * thêm tiền tố; DB lưu đúng mã đã gửi cổng để callback/đối soát khớp.
     */
    default String createTxnRef(String baseRef, Instant createdAt) {
        return baseRef;
    }

    /** Dựng URL thanh toán, dùng thời điểm hiện tại. */
    String buildPaymentUrl(PaymentRequest request);

    /** Dựng URL thanh toán với mốc thời gian của phiên (mốc này cũng là mốc của giao dịch). */
    String buildPaymentUrl(PaymentRequest request, Instant createdAt);

    /**
     * Truy vấn trạng thái giao dịch tại cổng (server-to-server), đã chuẩn hoá — job đối soát dùng
     * chung cho mọi provider.
     *
     * @throws IllegalStateException response không xác thực được (sai chữ ký, body rỗng)
     */
    TransactionQueryResult queryTransaction(TransactionQuery query);

    /** Kiểm chữ ký trên dữ liệu cổng trả về, dùng cho Return URL và/hoặc callback. */
    boolean verifySignature(Map<String, String> params);

    /**
     * Xác thực và chuẩn hoá callback.
     *
     * @throws IllegalArgumentException chữ ký sai hoặc payload không đọc được
     */
    default GatewayNotification parseNotification(Map<String, String> payload) {
        throw new UnsupportedOperationException("Cổng " + provider() + " chưa hỗ trợ callback");
    }
}
