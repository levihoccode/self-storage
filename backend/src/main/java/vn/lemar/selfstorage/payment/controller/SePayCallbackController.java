package vn.lemar.selfstorage.payment.controller;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.dto.PaymentNotificationResult;

/**
 * Webhook biến động số dư SePay. Đây là nguồn xác nhận thanh toán của SePay — không có redirect.
 *
 * <p>Phải đọc <strong>raw body</strong> để verify HMAC-SHA256 (SePay ký
 * {@code timestamp + "." + rawBody}); parse lại JSON sẽ làm lệch chữ ký.
 */
@RestController
@RequestMapping("/api/payments/sepay")
@ConditionalOnProperty(name = "payment.provider", havingValue = "sepay")
public class SePayCallbackController {

    private static final Logger LOG = LoggerFactory.getLogger(SePayCallbackController.class);

    private final PaymentService paymentService;
    private final PaymentGateway gateway;
    private final ObjectMapper objectMapper;

    public SePayCallbackController(PaymentService paymentService, PaymentGateway gateway,
            ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @SecurityRequirements
    @Operation(summary = "Webhook biến động số dư SePay",
            description = "SePay gọi server-to-server khi tiền vào tài khoản. Xác thực HMAC-SHA256 "
                    + "trên raw body; trả `{success:true}` để SePay dừng retry.")
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp) {
        if (!gateway.verifyWebhook(rawBody, signature, timestamp)) {
            LOG.warn("SePay webhook bị từ chối vì sai chữ ký/timestamp");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Invalid signature"));
        }
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, new TypeReference<>() {
            });
            Map<String, String> payload = new LinkedHashMap<>();
            body.forEach((key, value) -> payload.put(key, value == null ? null : String.valueOf(value)));
            PaymentNotificationResult result = paymentService.handleNotification(
                    gateway.parseNotification(payload));
            if (result == PaymentNotificationResult.NOT_FOUND
                    || result == PaymentNotificationResult.INVALID_AMOUNT) {
                LOG.error("SePay webhook không khớp giao dịch: {}", payload);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.warn("SePay webhook payload lỗi: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", false));
        }
    }
}
