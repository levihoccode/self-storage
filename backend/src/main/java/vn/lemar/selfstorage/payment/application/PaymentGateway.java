package vn.lemar.selfstorage.payment.application;

import java.time.Instant;
import java.util.Map;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;

/**
 * Cổng thanh toán, tách khỏi nghiệp vụ để đổi nhà cung cấp không phải sửa flow nào.
 *
 * <p>Adapter chịu trách nhiệm: sinh mã giao dịch đúng định dạng của cổng, dựng URL, xác thực
 * callback và chuẩn hoá kết quả về {@link GatewayNotification}.
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

    /** Cổng có dựng lại được URL của một phiên cũ không. ZaloPay không hỗ trợ. */
    default boolean supportsResume() {
        return true;
    }

    /** Dựng URL thanh toán, dùng thời điểm hiện tại. */
    String buildPaymentUrl(PaymentRequest request);

    /** Dựng lại URL của một phiên đã mở, giữ nguyên mốc thời gian gốc. */
    String buildPaymentUrl(PaymentRequest request, Instant createdAt);

    /** Kiểm chữ ký trên dữ liệu cổng trả về, dùng cho Return URL và/hoặc callback. */
    boolean verifySignature(Map<String, String> params);

    /**
     * Xác thực và chuẩn hoá callback/IPN.
     *
     * @throws IllegalArgumentException chữ ký sai hoặc payload không đọc được
     */
    default GatewayNotification parseNotification(Map<String, String> payload) {
        throw new UnsupportedOperationException("Cổng " + provider() + " chưa hỗ trợ callback");
    }

    /**
     * Xác thực webhook dạng raw body (SePay ký {@code timestamp + "." + rawBody}). Mặc định
     * không hỗ trợ; adapter nào dùng webhook raw body override.
     */
    default boolean verifyWebhook(String rawBody, String signature, String timestamp) {
        return false;
    }
}
