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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.application.dto.TransactionQuery;
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult;
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult.Outcome;
import vn.lemar.selfstorage.payment.config.PayOsProperties;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

/**
 * Adapter payOS theo docs/SDK chính thức: ký request tạo link trên 5 field, ký webhook trên toàn bộ
 * {@code data} theo key alphabet, tra cứu theo {@code orderCode}. HTTP thật được thay bằng server cục bộ.
 */
class PayOsGatewayTest {

    /** Checksum key mẫu trong trang "Kiểm tra dữ liệu với signature" của payOS. */
    private static final String DOC_CHECKSUM_KEY = "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";
    private static final String DOC_SIGNATURE = "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03";
    private static final String RETURN_URL = "http://localhost/return";
    private static final String CANCEL_URL = "http://localhost/cancel";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PayOsGateway gateway = gateway("http://unused");
    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> requestHeaders = new AtomicReference<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void orderCodeIsNumericAndStartsFromTheCreationMillis() {
        Instant at = Instant.parse("2026-10-30T03:00:00Z");

        String ref = gateway.createTxnRef("INV1T20261030100000ABCDE", at);

        assertThat(ref).matches("\\d{15}");
        assertThat(Long.parseLong(ref) / 100).isEqualTo(at.toEpochMilli());
        assertThat(Long.parseLong(ref)).isLessThan(9_007_199_254_740_991L);
    }

    @Test
    void descriptionFitsTheNineCharacterBankLimit() {
        assertThat(PayOsGateway.description("176179680000042")).isEqualTo("SS0000042");
        assertThat(PayOsGateway.description("42")).isEqualTo("SS42");
    }

    @Test
    void createLinkSignsTheFiveDocumentedFieldsAndSendsTheSessionExpiry() throws Exception {
        AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
        String base = startServer(sent, 200, "{\"code\":\"00\",\"desc\":\"success\",\"data\":"
                + "{\"checkoutUrl\":\"https://pay.payos.vn/web/abc\",\"status\":\"PENDING\"}}");
        PayOsGateway local = gateway(base);
        Instant createdAt = Instant.parse("2026-10-30T03:00:00Z");

        String url = local.buildPaymentUrl(new PaymentRequest("176179680000042", new BigDecimal("500000.00"),
                "Dat coc khoang A-01", "1.2.3.4"), createdAt);

        assertThat(url).isEqualTo("https://pay.payos.vn/web/abc");
        assertThat(requestPath.get()).isEqualTo("/v2/payment-requests");
        assertThat(requestHeaders.get()).containsEntry("x-client-id", "client").containsEntry("x-api-key", "api");
        Map<String, Object> body = sent.get();
        assertThat(((Number) body.get("orderCode")).longValue()).isEqualTo(176179680000042L);
        assertThat(((Number) body.get("amount")).longValue()).isEqualTo(500000L);
        assertThat(body.get("description")).isEqualTo("SS0000042");
        assertThat(((Number) body.get("expiredAt")).longValue())
                .isEqualTo(createdAt.plusSeconds(900).getEpochSecond());
        String signed = "amount=500000&cancelUrl=" + CANCEL_URL + "&description=SS0000042"
                + "&orderCode=176179680000042&returnUrl=" + RETURN_URL;
        assertThat(body.get("signature")).isEqualTo(PayOsGateway.hmac(signed, "checksum"));
    }

    @Test
    void createLinkFailureCarriesTheGatewayMessage() throws Exception {
        String base = startServer(new AtomicReference<>(), 200,
                "{\"code\":\"231\",\"desc\":\"Đơn thanh toán đã tồn tại\",\"data\":null}");

        assertThatThrownBy(() -> gateway(base).buildPaymentUrl(
                new PaymentRequest("176179680000042", new BigDecimal("2000"), "x", "1.2.3.4"), Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Đơn thanh toán đã tồn tại");
    }

    @Test
    void createLinkWithoutConfigFailsFast() {
        PayOsGateway unconfigured = new PayOsGateway(
                new PayOsProperties("", "", "", "http://x", RETURN_URL, CANCEL_URL), objectMapper);

        assertThatThrownBy(() -> unconfigured.buildPaymentUrl(
                new PaymentRequest("1", new BigDecimal("2000"), "x", "1.2.3.4")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Thiếu cấu hình payOS");
    }

    @Test
    void webhookSignatureMatchesTheDocumentedVector() {
        PayOsGateway docs = new PayOsGateway(
                new PayOsProperties("c", "a", DOC_CHECKSUM_KEY, "http://x", RETURN_URL, CANCEL_URL), objectMapper);

        assertThat(PayOsGateway.hmac(docs.webhookSignatureData(docData()), DOC_CHECKSUM_KEY))
                .isEqualTo(DOC_SIGNATURE);
    }

    @Test
    void validWebhookBecomesASuccessNotification() throws Exception {
        PayOsGateway docs = new PayOsGateway(
                new PayOsProperties("c", "a", DOC_CHECKSUM_KEY, "http://x", RETURN_URL, CANCEL_URL), objectMapper);
        String data = objectMapper.writeValueAsString(docData());

        GatewayNotification notification = docs.parseNotification(Map.of("data", data, "signature", DOC_SIGNATURE));

        assertThat(notification.txnRef()).isEqualTo("123");
        assertThat(notification.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(notification.amount()).isEqualByComparingTo("3000");
        assertThat(notification.gatewayTransactionNo()).isEqualTo("TF230204212323");
        // 18:25 giờ Việt Nam = 11:25 UTC
        assertThat(notification.paidAt()).isEqualTo(Instant.parse("2023-02-04T11:25:00Z"));
        assertThat(notification.rawPayload()).isEqualTo(data);
    }

    @Test
    void webhookWithAnotherTransactionCodeIsNotTreatedAsPaid() throws Exception {
        Map<String, Object> data = docData();
        data.put("code", "01");
        String json = objectMapper.writeValueAsString(data);

        GatewayNotification notification = gateway.parseNotification(Map.of("data", json,
                "signature", PayOsGateway.hmac(gateway.webhookSignatureData(data), "checksum")));

        assertThat(notification.status()).isNull();
    }

    @Test
    void tamperedWebhookIsRejected() throws Exception {
        Map<String, Object> data = docData();
        data.put("amount", 1);

        assertThatThrownBy(() -> gateway.parseNotification(Map.of(
                "data", objectMapper.writeValueAsString(data), "signature", DOC_SIGNATURE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sai chữ ký");
    }

    @Test
    void webhookWithoutDataOrSignatureIsRejected() {
        Map<String, String> missing = new LinkedHashMap<>();
        missing.put("signature", "x");

        assertThatThrownBy(() -> gateway.parseNotification(missing))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", "{}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void webhookWithoutOrderCodeIsRejected() throws Exception {
        Map<String, Object> data = docData();
        data.remove("orderCode");

        assertThatThrownBy(() -> gateway.parseNotification(Map.of("data", objectMapper.writeValueAsString(data),
                "signature", PayOsGateway.hmac(gateway.webhookSignatureData(data), "checksum"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderCode");
    }

    @Test
    void verifySignatureAcceptsOnlyTheChecksumKey() throws Exception {
        Map<String, Object> data = docData();
        String json = objectMapper.writeValueAsString(data);

        assertThat(gateway.verifySignature(Map.of("data", json,
                "signature", PayOsGateway.hmac(gateway.webhookSignatureData(data), "checksum")))).isTrue();
        assertThat(gateway.verifySignature(Map.of("data", json, "signature", DOC_SIGNATURE))).isFalse();
        assertThat(gateway.verifySignature(Map.of("data", json))).isFalse();
    }

    @Test
    void signatureDataTurnsNullIntoEmptyAndSortsArrayItems() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("b", null);
        data.put("a", List.of(Map.of("z", 1, "y", 2)));

        assertThat(gateway.webhookSignatureData(data)).isEqualTo("a=[{\"y\":2,\"z\":1}]&b=");
    }

    @Test
    void queryAsksByOrderCodeAndMapsPaid() throws Exception {
        String base = startServer(new AtomicReference<>(), 200, "{\"code\":\"00\",\"desc\":\"success\",\"data\":"
                + "{\"orderCode\":176179680000042,\"amount\":500000,\"amountPaid\":500000,\"status\":\"PAID\","
                + "\"transactions\":[{\"reference\":\"FT001\",\"amount\":500000,"
                + "\"transactionDateTime\":\"2026-10-30 10:05:00\"}]}}");

        TransactionQueryResult result = gateway(base).queryTransaction(
                new TransactionQuery("176179680000042", "x", Instant.now(), "1.2.3.4"));

        assertThat(requestPath.get()).isEqualTo("/v2/payment-requests/176179680000042");
        assertThat(requestHeaders.get()).containsEntry("x-client-id", "client");
        assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(result.amount()).isEqualByComparingTo("500000");
        assertThat(result.transactionNo()).isEqualTo("FT001");
        assertThat(result.paidAt()).isEqualTo(Instant.parse("2026-10-30T03:05:00Z"));
    }

    /** Response thật của payOS khi tra cứu orderCode không tồn tại: HTTP 200, {@code code = 101}. */
    @Test
    void queryOfAMissingLinkIsNotFound() throws Exception {
        String base = startServer(new AtomicReference<>(), 200,
                "{\"code\":\"101\",\"desc\":\"Mã thanh toán không tồn tại\"}");

        TransactionQueryResult result = gateway(base).queryTransaction(
                new TransactionQuery("999999999999999", "x", Instant.now(), "1.2.3.4"));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_FOUND);
        assertThat(result.message()).isEqualTo("Mã thanh toán không tồn tại");
    }

    @Test
    void queryErrorResponseIsUnknownEvenOnHttpError() throws Exception {
        String base = startServer(new AtomicReference<>(), 401,
                "{\"code\":\"20\",\"desc\":\"Unauthorized\",\"data\":null}");

        TransactionQueryResult result = gateway(base).queryTransaction(
                new TransactionQuery("1", "x", Instant.now(), "1.2.3.4"));

        assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(result.message()).isEqualTo("Unauthorized");
    }

    @ParameterizedTest
    @CsvSource({
        "PAID, SUCCESS",
        "CANCELLED, FAILED",
        "EXPIRED, FAILED",
        "FAILED, FAILED",
        "PENDING, PENDING",
        "PROCESSING, PENDING",
        "UNDERPAID, PENDING",
        "SOMETHING_NEW, UNKNOWN"
    })
    void paymentLinkStatusMapsToAnOutcome(String status, Outcome expected) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("amountPaid", 500000);
        Map<String, Object> response = Map.of("code", "00", "desc", "success", "data", data);

        TransactionQueryResult result = gateway.toQueryResult(response);

        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.message()).isEqualTo(status);
        if (expected != Outcome.SUCCESS) {
            assertThat(result.amount()).isNull();
        }
    }

    @Test
    void paidAtReadsIsoValuesAndIgnoresGarbage() {
        assertThat(PayOsGateway.paidAt("2026-10-30T03:05:00Z")).isEqualTo(Instant.parse("2026-10-30T03:05:00Z"));
        // dạng tra cứu link trả thật
        assertThat(PayOsGateway.paidAt("2026-10-30T18:18:00+07:00"))
                .isEqualTo(Instant.parse("2026-10-30T11:18:00Z"));
        assertThat(PayOsGateway.paidAt("2026-10-30T10:05:00")).isEqualTo(Instant.parse("2026-10-30T03:05:00Z"));
        assertThat(PayOsGateway.paidAt("not a time")).isNull();
        assertThat(PayOsGateway.paidAt(" ")).isNull();
        assertThat(PayOsGateway.paidAt(null)).isNull();
    }

    @Test
    void providerIsPayos() {
        assertThat(gateway.provider()).isEqualTo("payos");
    }

    /** {@code data} mẫu đúng nguyên văn trang signature của payOS. */
    private static Map<String, Object> docData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 123);
        data.put("amount", 3000);
        data.put("description", "VQRIO123");
        data.put("accountNumber", "12345678");
        data.put("reference", "TF230204212323");
        data.put("transactionDateTime", "2023-02-04 18:25:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "124c33293c43417ab7879e14c8d9eb18");
        data.put("code", "00");
        data.put("desc", "Thành công");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }

    private PayOsGateway gateway(String baseUrl) {
        return new PayOsGateway(new PayOsProperties("client", "api", "checksum", baseUrl, RETURN_URL, CANCEL_URL),
                objectMapper);
    }

    private String startServer(AtomicReference<Map<String, Object>> sent, int status, String response)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestHeaders.set(Map.of(
                    "x-client-id", String.valueOf(exchange.getRequestHeaders().getFirst("x-client-id")),
                    "x-api-key", String.valueOf(exchange.getRequestHeaders().getFirst("x-api-key"))));
            byte[] request = exchange.getRequestBody().readAllBytes();
            if (request.length > 0) {
                sent.set(objectMapper.readValue(request, new TypeReference<Map<String, Object>>() {
                }));
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
