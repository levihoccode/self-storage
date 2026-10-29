package vn.lemar.selfstorage.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.SePayGateway;
import vn.lemar.selfstorage.payment.application.dto.PaymentNotificationResult;
import vn.lemar.selfstorage.payment.config.SePayProperties;

class SePayCallbackControllerTest {

    private static final String SECRET = "test-secret";
    private static final String BODY = """
            {"code":"DH123","content":"DH123 chuyen tien","transferType":"in",
             "transferAmount":50000,"referenceCode":"FT123","transactionDate":"2026-10-29 14:30:00"}""";

    private final PaymentService paymentService = mock(PaymentService.class);
    private final SePayGateway gateway = new SePayGateway(new SePayProperties(
            "MBBank", "0123456789", "NGUYEN VAN A",
            "https://vietqr.app/img", SECRET));
    private final SePayCallbackController controller =
            new SePayCallbackController(paymentService, gateway, new ObjectMapper());

    @Test
    void validWebhookIsAcknowledged() throws Exception {
        when(paymentService.handleNotification(any())).thenReturn(PaymentNotificationResult.ACCEPTED);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = "sha256=" + hmac(timestamp + "." + BODY, SECRET);

        var response = controller.webhook(BODY, signature, timestamp);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
    }

    @Test
    void invalidSignatureIsRejected() {
        var response = controller.webhook(BODY, "sha256=bad", String.valueOf(Instant.now().getEpochSecond()));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    private static String hmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
