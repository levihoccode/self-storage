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
    static final int RETURN_SUCCESS = 1;
    static final int RETURN_RETRY = 0;
    static final int RETURN_INVALID = 2;

    private final PaymentService paymentService;
    private final PaymentGateway gateway;

    public ZaloPayCallbackController(PaymentService paymentService, PaymentGateway gateway) {
        this.paymentService = paymentService;
        this.gateway = gateway;
    }

    @SecurityRequirements
    @Operation(summary = "Callback của ZaloPay",
            description = "ZaloPay gọi server-to-server khi khách thanh toán thành công; body "
                    + "`{data, mac, type}`, `mac` = HMAC-SHA256(key2, `data`). `return_code`: `1` đã ghi "
                    + "nhận / đã xử lý trước đó; `0` không tìm thấy giao dịch — ZaloPay gọi lại; `2` sai chữ "
                    + "ký, sai `app_id`, lệch số tiền hoặc payload lỗi — không ghi gì.")
    @PostMapping("/callback")
    public Map<String, Object> callback(@RequestBody Map<String, Object> body) {
        Map<String, String> payload = new LinkedHashMap<>();
        body.forEach((key, value) -> payload.put(key, value == null ? null : String.valueOf(value)));
        GatewayNotification notification;
        try {
            notification = gateway.parseNotification(payload);
        } catch (IllegalArgumentException e) {
            LOG.warn("ZaloPay callback bị từ chối: {}", e.getMessage());
            return response(RETURN_INVALID, "invalid callback");
        }
        PaymentNotificationResult result;
        try {
            result = paymentService.handleNotification(notification);
        } catch (RuntimeException e) {
            // Lỗi phía hệ thống (DB...) — trả 0 để ZaloPay gọi lại, không coi là callback sai.
            LOG.error("ZaloPay callback {} lỗi khi ghi nhận", notification.txnRef(), e);
            return response(RETURN_RETRY, "retry");
        }
        LOG.info("ZaloPay callback {} -> {}", notification.txnRef(), result);
        return switch (result) {
            case ACCEPTED, ALREADY_SETTLED, IGNORED -> response(RETURN_SUCCESS, "success");
            case NOT_FOUND -> response(RETURN_RETRY, "retry");
            case INVALID_AMOUNT -> response(RETURN_INVALID, "invalid amount");
        };
    }

    private static Map<String, Object> response(int returnCode, String message) {
        return Map.of("return_code", returnCode, "return_message", message);
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
