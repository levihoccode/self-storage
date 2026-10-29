package vn.lemar.selfstorage.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.config.SePayProperties;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

class SePayGatewayTest {

    private static final String SECRET = "test-secret";

    private final SePayGateway gateway = new SePayGateway(new SePayProperties(
            "MBBank", "0123456789", "NGUYEN VAN A",
            "https://vietqr.app/img", SECRET));

    @Test
    void qrUrlCarriesAccountAmountAndTransferContent() {
        String url = gateway.buildPaymentUrl(new PaymentRequest(
                "DH 123", new BigDecimal("50000"), "Dat coc", "127.0.0.1"), Instant.now());

        assertThat(url).startsWith("https://vietqr.app/img?")
                .contains("acc=0123456789")
                .contains("bank=MBBank")
                .contains("amount=50000")
                .contains("des=DH+123");
    }

    @Test
    void incomingTransferIsMappedToSuccess() {
        GatewayNotification notification = gateway.parseNotification(Map.of(
                "code", "DH123",
                "content", "DH123 chuyen tien",
                "transferType", "in",
                "transferAmount", "50000",
                "referenceCode", "FT123",
                "transactionDate", "2026-10-29 14:30:00"));

        assertThat(notification.txnRef()).isEqualTo("DH123");
        assertThat(notification.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(notification.amount()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(notification.gatewayTransactionNo()).isEqualTo("FT123");
    }

    @Test
    void outgoingTransferIsIgnored() {
        GatewayNotification notification = gateway.parseNotification(Map.of(
                "code", "DH123", "transferType", "out", "transferAmount", "50000"));

        assertThat(notification.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void missingCodeAndContentIsRejected() {
        assertThatThrownBy(() -> gateway.parseNotification(Map.of("transferType", "in")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hmacWebhookVerificationUsesTimestampAndRawBody() throws Exception {
        String body = "{\"code\":\"DH123\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = "sha256=" + hmac(timestamp + "." + body, SECRET);

        assertThat(gateway.verifyWebhook(body, signature, timestamp)).isTrue();
        assertThat(gateway.verifyWebhook(body, signature, "1")).isFalse();
        assertThat(gateway.verifyWebhook(body + " ", signature, timestamp)).isFalse();
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
