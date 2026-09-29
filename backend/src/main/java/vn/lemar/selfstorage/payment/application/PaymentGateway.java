package vn.lemar.selfstorage.payment.application;

import java.util.Map;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;

/**
 * Cổng thanh toán, tách khỏi nghiệp vụ để đổi nhà cung cấp không phải sửa flow nào
 * (issue #15 mục 6).
 */
public interface PaymentGateway {

    /** Dựng URL để redirect khách sang cổng thanh toán. */
    String buildPaymentUrl(PaymentRequest request);

    /**
     * Kiểm chữ ký trên dữ liệu cổng trả về, dùng cho cả Return URL lẫn IPN.
     *
     * @param params toàn bộ tham số nhận được, kể cả `vnp_SecureHash`
     */
    boolean verifySignature(Map<String, String> params);
}
