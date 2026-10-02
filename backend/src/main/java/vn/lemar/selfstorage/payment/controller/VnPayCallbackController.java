package vn.lemar.selfstorage.payment.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.dto.IpnResponse;

/** Hai đường VNPay gọi ngược về sau khi khách thanh toán. */
@RestController
@RequestMapping("/api/payments/vnpay")
public class VnPayCallbackController {

    private static final Logger LOG = LoggerFactory.getLogger(VnPayCallbackController.class);

    private final PaymentService paymentService;
    private final PaymentGateway gateway;

    public VnPayCallbackController(PaymentService paymentService, PaymentGateway gateway) {
        this.paymentService = paymentService;
        this.gateway = gateway;
    }

    /**
     * Nơi trình duyệt khách được đẩy về. Chỉ hiển thị kết quả, **không** cập nhật dữ liệu —
     * tham số trên thanh địa chỉ do khách kiểm soát nên không đủ tin cậy để ghi nhận đã trả tiền.
     */
    @GetMapping("/return")
    public ResponseEntity<Map<String, Object>> handleReturn(@RequestParam Map<String, String> params) {
        boolean validSignature = gateway.verifySignature(params);
        return ResponseEntity.ok(Map.of(
                "validSignature", validSignature,
                "responseCode", params.getOrDefault("vnp_ResponseCode", ""),
                "txnRef", params.getOrDefault("vnp_TxnRef", "")));
    }

    /**
     * Nơi server VNPay gọi vào. Đây mới là nguồn xác nhận thanh toán.
     *
     * <p>Lỗi ngoài dự kiến được dịch thành {@code 99} thay vì để bật lên HTTP 500: VNPay đọc được
     * mã này và sẽ retry. Bắt ở đây chứ không bắt trong service để transaction vẫn rollback.
     */
    @GetMapping("/ipn")
    public ResponseEntity<IpnResponse> handleIpn(@RequestParam Map<String, String> params) {
        try {
            return ResponseEntity.ok(paymentService.handleIpn(params));
        } catch (RuntimeException e) {
            LOG.error("IPN lỗi ngoài dự kiến, vnp_TxnRef={}", params.get("vnp_TxnRef"), e);
            return ResponseEntity.ok(IpnResponse.unknownError());
        }
    }
}
