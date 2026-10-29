package vn.lemar.selfstorage.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.application.dto.TransactionQuery;
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult;
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult.Outcome;
import vn.lemar.selfstorage.payment.config.ZaloPayProperties;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

/**
 * Adapter ZaloPay theo docs: callback {@code {data, mac, type}} không có field trạng thái; {@code /v2/query}
 * trả {@code return_code} 1/2/3. HTTP thật được thay bằng server cục bộ để kiểm body gửi cổng.
 */
class ZaloPayGatewayTest {

    private static final String APP_ID = "553";
    private static final String KEY1 = "test-key1";
    private static final String KEY2 = "test-key2";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ZaloPayGateway gateway = gateway("https://create", "https://query");
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void transactionRefCarriesTheVietnamDatePrefix() {
        Instant at = Instant.parse("2026-10-28T17:30:00Z"); // 29/10 00:30 GMT+7

        assertThat(gateway.createTxnRef("INV1T20261029003000ABCDE", at))
                .isEqualTo("261029_INV1T20261029003000ABCDE");
    }

    @Test
    void transactionRefIsCutAtFortyCharacters() {
        String ref = gateway.createTxnRef("X".repeat(60), Instant.parse("2026-10-28T17:30:00Z"));

        assertThat(ref).hasSize(40).startsWith("261029_");
    }

    @Test
    void createOrderSendsTheSessionExpiryAndSignsWithKey1() throws Exception {
        AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
        String base = startServer(sent, "{\"return_code\":1,\"order_url\":\"https://sb-gw/order/abc\"}");
        ZaloPayGateway local = gateway(base + "/v2/create", base + "/v2/query");
        Instant createdAt = Instant.parse("2026-10-28T17:30:00Z");

        String url = local.buildPaymentUrl(new PaymentRequest("261029_INV1", new BigDecimal("500000"),
                "Dat coc khoang A-01", "1.2.3.4"), createdAt);

        assertThat(url).isEqualTo("https://sb-gw/order/abc");
        Map<String, Object> body = sent.get();
        assertThat(((Number) body.get("expire_duration_seconds")).longValue()).isEqualTo(900L);
        assertThat(body.get("app_trans_id")).isEqualTo("261029_INV1");
        assertThat(((Number) body.get("amount")).longValue()).isEqualTo(500000L);
        String macInput = String.join("|", APP_ID, "261029_INV1", "self-storage", "500000",
                String.valueOf(createdAt.toEpochMilli()), String.valueOf(body.get("embed_data")), "[]");
        assertThat(body.get("mac")).isEqualTo(ZaloPayGateway.hmac(macInput, KEY1));
    }

    @Test
    void createOrderFailureIsRaised() throws Exception {
        String base = startServer(new AtomicReference<>(), "{\"return_code\":2,\"return_message\":\"fail\"}");
        ZaloPayGateway local = gateway(base + "/v2/create", base + "/v2/query");

        assertThatThrownBy(() -> local.buildPaymentUrl(new PaymentRequest("261029_INV1",
                new BigDecimal("500000"), "Dat coc", "1.2.3.4")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validCallbackWithoutStatusIsASuccess() throws Exception {
        String raw = objectMapper.writeValueAsString(callbackData());

        GatewayNotification notification = gateway.parseNotification(
                Map.of("data", raw, "mac", ZaloPayGateway.hmac(raw, KEY2), "type", "1"));

        assertThat(notification.txnRef()).isEqualTo("261029_INV1");
        assertThat(notification.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(notification.amount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(notification.gatewayTransactionNo()).isEqualTo("240313000000123");
        assertThat(notification.paidAt()).isEqualTo(Instant.ofEpochMilli(1793252802000L));
        assertThat(notification.rawPayload()).isEqualTo(raw);
    }

    @Test
    void rejectsACallbackWithABadSignature() throws Exception {
        String raw = objectMapper.writeValueAsString(callbackData());

        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", raw, "mac", "bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chữ ký");
    }

    @Test
    void rejectsACallbackSignedWithKey1() throws Exception {
        String raw = objectMapper.writeValueAsString(callbackData());

        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", raw,
                "mac", ZaloPayGateway.hmac(raw, KEY1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsACallbackForAnotherApp() throws Exception {
        Map<String, Object> data = callbackData();
        data.put("app_id", 999);
        String raw = objectMapper.writeValueAsString(data);

        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", raw,
                "mac", ZaloPayGateway.hmac(raw, KEY2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app_id");
    }

    @Test
    void rejectsACallbackWithoutDataOrMac() {
        assertThatThrownBy(() -> gateway.parseNotification(Map.of("mac", "m")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsACallbackMissingTheAmount() throws Exception {
        Map<String, Object> data = callbackData();
        data.remove("amount");
        String raw = objectMapper.writeValueAsString(data);

        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", raw,
                "mac", ZaloPayGateway.hmac(raw, KEY2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsACallbackWithAMalformedAmount() throws Exception {
        Map<String, Object> data = callbackData();
        data.put("amount", "mười nghìn");
        String raw = objectMapper.writeValueAsString(data);

        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", raw,
                "mac", ZaloPayGateway.hmac(raw, KEY2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsACallbackWithUnreadableData() {
        String raw = "not-json";

        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", raw,
                "mac", ZaloPayGateway.hmac(raw, KEY2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryBodyIsSignedWithAppIdTransIdAndKey1() {
        Map<String, Object> body = gateway.queryBody("261029_INV1");

        assertThat(body.get("app_trans_id")).isEqualTo("261029_INV1");
        assertThat(body.get("mac")).isEqualTo(ZaloPayGateway.hmac(APP_ID + "|261029_INV1|" + KEY1, KEY1));
    }

    @Test
    void queryPaidOrderIsASuccessWithAmount() {
        TransactionQueryResult result = gateway.toQueryResult(Map.of("return_code", 1, "return_message",
                "Giao dịch thành công", "amount", 10000, "zp_trans_id", 240313000000123L,
                "server_time", 1793252802000L));

        assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(result.amount()).isEqualByComparingTo("10000");
        assertThat(result.transactionNo()).isEqualTo("240313000000123");
        assertThat(result.paidAt()).isEqualTo(Instant.ofEpochMilli(1793252802000L));
    }

    @Test
    void queryFailedOrderCarriesNoAmount() {
        TransactionQueryResult result = gateway.toQueryResult(Map.of("return_code", 2,
                "sub_return_code", -54, "return_message", "Giao dịch thất bại", "amount", 0));

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.amount()).isNull();
    }

    @Test
    void queryUnknownOrderIsNotFound() {
        assertThat(gateway.toQueryResult(Map.of("return_code", 2, "sub_return_code", -101)).outcome())
                .isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void queryProcessingOrderIsPending() {
        assertThat(gateway.toQueryResult(Map.of("return_code", 3, "is_processing", true)).outcome())
                .isEqualTo(Outcome.PENDING);
    }

    @Test
    void queryUnexpectedCodeIsUnknown() {
        assertThat(gateway.toQueryResult(Map.of("return_code", "abc")).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void queryTransactionCallsTheQueryEndpoint() throws Exception {
        AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
        String base = startServer(sent, "{\"return_code\":3,\"is_processing\":true}");
        ZaloPayGateway local = gateway(base + "/v2/create", base + "/v2/query");

        TransactionQueryResult result = local.queryTransaction(
                new TransactionQuery("261029_INV1", "Dat coc", Instant.now(), "127.0.0.1"));

        assertThat(result.outcome()).isEqualTo(Outcome.PENDING);
        assertThat(sent.get().get("app_trans_id")).isEqualTo("261029_INV1");
    }

    @Test
    void missingConfigurationIsRejected() {
        ZaloPayGateway unconfigured = new ZaloPayGateway(new ZaloPayProperties(
                "", "", "", "", "", null, null), objectMapper);

        assertThatThrownBy(() -> unconfigured.queryTransaction(
                new TransactionQuery("261029_INV1", "Dat coc", Instant.now(), "127.0.0.1")))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Payload callback đúng mẫu docs ZaloPay — không có field {@code status}. */
    private static Map<String, Object> callbackData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_id", 553);
        data.put("app_trans_id", "261029_INV1");
        data.put("app_time", 1793252700000L);
        data.put("app_user", "self-storage");
        data.put("amount", 10000);
        data.put("embed_data", "{}");
        data.put("item", "[]");
        data.put("zp_trans_id", 240313000000123L);
        data.put("server_time", 1793252802000L);
        data.put("channel", 38);
        data.put("merchant_user_id", "user");
        data.put("user_fee_amount", 0);
        data.put("discount_amount", 0);
        return data;
    }

    private ZaloPayGateway gateway(String createUrl, String queryUrl) {
        return new ZaloPayGateway(new ZaloPayProperties(APP_ID, KEY1, KEY2, createUrl, queryUrl,
                "https://callback", "https://return"), objectMapper);
    }

    private String startServer(AtomicReference<Map<String, Object>> sent, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            sent.set(objectMapper.readValue(request, new TypeReference<Map<String, Object>>() {
            }));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
