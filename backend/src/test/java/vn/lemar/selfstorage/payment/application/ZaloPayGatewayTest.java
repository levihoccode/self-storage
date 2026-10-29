package vn.lemar.selfstorage.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.config.ZaloPayProperties;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

class ZaloPayGatewayTest {

    private static final String APP_ID = "553";
    private static final String KEY1 = "test-key1";
    private static final String KEY2 = "test-key2";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ZaloPayGateway gateway = new ZaloPayGateway(new ZaloPayProperties(
            APP_ID, KEY1, KEY2, "https://create", "https://query",
            "https://callback", "https://return"), objectMapper);

    @Test
    void transactionRefCarriesTheVietnamDatePrefix() {
        Instant at = Instant.parse("2026-10-28T17:30:00Z"); // 29/10 00:30 GMT+7

        assertThat(gateway.createTxnRef("INV1T20261029003000ABCDE", at))
                .isEqualTo("261029_INV1T20261029003000ABCDE");
    }

    @Test
    void parsesASuccessCallbackAfterVerifyingTheSignature() throws Exception {
        Map<String, Object> data = data(1);
        String raw = objectMapper.writeValueAsString(data);

        GatewayNotification notification = gateway.parseNotification(
                Map.of("data", raw, "mac", ZaloPayGateway.hmac(raw, KEY2)));

        assertThat(notification.txnRef()).isEqualTo("261029_INV1");
        assertThat(notification.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(notification.amount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(notification.gatewayTransactionNo()).isEqualTo("ZP123");
        assertThat(notification.rawPayload()).isEqualTo(raw);
    }

    @Test
    void mapsFailureAndProcessingStatuses() throws Exception {
        String failure = objectMapper.writeValueAsString(data(2));
        String processing = objectMapper.writeValueAsString(data(3));

        assertThat(gateway.parseNotification(Map.of("data", failure,
                "mac", ZaloPayGateway.hmac(failure, KEY2))).status())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(gateway.parseNotification(Map.of("data", processing,
                "mac", ZaloPayGateway.hmac(processing, KEY2))).status())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void rejectsACallbackWithABadSignature() throws Exception {
        String raw = objectMapper.writeValueAsString(data(1));

        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", raw, "mac", "bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chữ ký");
    }

    @Test
    void rejectsACallbackForAnotherApp() throws Exception {
        Map<String, Object> data = data(1);
        data.put("app_id", "999");
        String raw = objectMapper.writeValueAsString(data);

        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", raw,
                "mac", ZaloPayGateway.hmac(raw, KEY2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app_id");
    }

    private static Map<String, Object> data(int status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_id", APP_ID);
        data.put("app_trans_id", "261029_INV1");
        data.put("amount", 10000);
        data.put("status", status);
        data.put("zp_trans_id", "ZP123");
        data.put("server_time", 1793252802000L);
        return data;
    }
}
