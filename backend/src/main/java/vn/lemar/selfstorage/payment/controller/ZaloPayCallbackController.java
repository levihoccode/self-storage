package vn.lemar.selfstorage.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentNotificationResult;

/**
 * Callback/Return của ZaloPay.
 *
 * <p>Callback là JSON {@code {data, mac}}; adapter xác thực chữ ký rồi chuẩn hoá. ZaloPay chỉ
 * ngừng retry khi nhận {@code return_code=1}, nên lỗi tạm thời trả {@code 0}.
 */
@RestController
@RequestMapping("/api/payments/zalopay")
@ConditionalOnProperty(name = "payment.provider", havingValue = "zalopay")
public class ZaloPayCallbackController {

    private static final Logger LOG = LoggerFactory.getLogger(ZaloPayCallbackController.class);

    private final PaymentService paymentService;
    private final PaymentGateway gateway;

    public ZaloPayCallbackController(PaymentService paymentService, PaymentGateway gateway) {
        this.paymentService = paymentService;
        this.gateway = gateway;
    }

    @SecurityRequirements
    @Operation(summary = "Callback IPN của ZaloPay",
            description = "ZaloPay gọi server-to-server sau khi thanh toán. Chữ ký HMAC-SHA256 trên "
                    + "`data` với key2. Trả `return_code=1` để dừng retry, `0` để ZaloPay gọi lại.")
    @PostMapping("/callback")
    public Map<String, Object> callback(@RequestBody Map<String, Object> body) {
        Map<String, String> payload = new LinkedHashMap<>();
        body.forEach((key, value) -> payload.put(key, value == null ? null : String.valueOf(value)));
        try {
            GatewayNotification notification = gateway.parseNotification(payload);
            PaymentNotificationResult result = paymentService.handleNotification(notification);
            LOG.info("ZaloPay callback {} -> {}", notification.txnRef(), result);
            boolean acknowledged = switch (result) {
                case ACCEPTED, ALREADY_SETTLED, IGNORED -> true;
                case NOT_FOUND, INVALID_AMOUNT -> false;
            };
            return acknowledged
                    ? Map.of("return_code", 1, "return_message", "success")
                    : Map.of("return_code", 0, "return_message", "retry");
        } catch (RuntimeException e) {
            LOG.warn("ZaloPay callback bị từ chối: {}", e.getMessage());
            return Map.of("return_code", 0, "return_message", "invalid callback");
        }
    }

    @SecurityRequirements
    @Operation(summary = "Return URL của ZaloPay",
            description = "Chỉ hiển thị kết quả cho khách; không ghi dữ liệu. Nguồn xác nhận là callback.")
    @GetMapping("/return")
    public Map<String, Object> returnPage(@RequestParam Map<String, String> params) {
        return Map.of(
                "provider", "zalopay",
                "status", params.getOrDefault("status", ""),
                "appTransId", params.getOrDefault("apptransid", ""));
    }
}
