package vn.lemar.selfstorage.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Webhook/Return của payOS.
 *
 * <p>payOS chỉ cần HTTP 2xx để coi webhook đã giao. Khi đăng ký URL ({@code confirm-webhook}),
 * payOS gửi một giao dịch mẫu không có trong DB và chỉ lưu URL nếu nhận 2xx — nên webhook đúng chữ
 * ký luôn trả 200, kể cả khi không tìm thấy giao dịch; giao dịch lỡ webhook do job đối soát bắt lại.
 */
@RestController
@RequestMapping("/api/payments/payos")
@ConditionalOnProperty(name = "payment.provider", havingValue = "payos")
public class PayOsWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(PayOsWebhookController.class);

    private final PaymentService paymentService;
    private final PaymentGateway gateway;
    private final ObjectMapper objectMapper;

    public PayOsWebhookController(PaymentService paymentService, PaymentGateway gateway,
                                  ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @SecurityRequirements
    @Operation(summary = "Webhook của payOS",
            description = "payOS gọi server-to-server khi có giao dịch; body `{code, desc, success, data, "
                    + "signature}`, `signature` = HMAC-SHA256(checksum key) trên `data` (key theo alphabet, "
                    + "`key=value&…`). `200` mọi webhook đúng chữ ký (đã ghi nhận, đã xử lý trước đó, không "
                    + "tìm thấy giao dịch, lệch số tiền); `400` sai chữ ký hoặc payload lỗi; `500` lỗi hệ "
                    + "thống khi ghi.")
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body) {
        GatewayNotification notification;
        try {
            notification = gateway.parseNotification(payload(body));
        } catch (IllegalArgumentException e) {
            LOG.warn("payOS webhook bị từ chối: {}", e.getMessage());
            return ResponseEntity.badRequest().body(response(false, "invalid webhook"));
        }
        PaymentNotificationResult result;
        try {
            result = paymentService.handleNotification(notification);
        } catch (RuntimeException e) {
            LOG.error("payOS webhook {} lỗi khi ghi nhận", notification.txnRef(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response(false, "error"));
        }
        LOG.info("payOS webhook {} -> {}", notification.txnRef(), result);
        return ResponseEntity.ok(response(true, result.name()));
    }

    /** Chuẩn hoá về {@code {data: <JSON của object data>, signature}} cho adapter. */
    private Map<String, String> payload(Map<String, Object> body) {
        Map<String, String> payload = new LinkedHashMap<>();
        Object data = body.get("data");
        if (data instanceof Map<?, ?>) {
            try {
                payload.put("data", objectMapper.writeValueAsString(data));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Không đọc được data payOS", e);
            }
        }
        Object signature = body.get("signature");
        payload.put("signature", signature == null ? null : String.valueOf(signature));
        return payload;
    }

    private static Map<String, Object> response(boolean success, String result) {
        return Map.of("success", success, "result", result);
    }

    @SecurityRequirements
    @Operation(summary = "Return/Cancel URL của payOS",
            description = "Chỉ hiển thị kết quả cho khách; không ghi dữ liệu. Nguồn xác nhận là webhook "
                    + "hoặc job đối soát.")
    @GetMapping("/return")
    public Map<String, Object> returnPage(@RequestParam Map<String, String> params) {
        return Map.of(
                "provider", "payos",
                "status", params.getOrDefault("status", ""),
                "cancel", params.getOrDefault("cancel", ""),
                "orderCode", params.getOrDefault("orderCode", ""));
    }
}
