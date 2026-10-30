package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.payment.application.PaymentReconciliationService;

/**
 * Luồng đặt cọc qua payOS trên Postgres + Redis thật: {@code POST /api/invoices/{id}/pay} → webhook →
 * hoá đơn/đơn/khoang/audit, cùng webhook lặp/sai/giao dịch mẫu của confirm-webhook, resume khi mất
 * cache, tiền về muộn và job đối soát qua {@code GET /v2/payment-requests/{orderCode}}. API payOS được
 * thay bằng server HTTP cục bộ; webhook ký bằng checksum key của test như cổng thật.
 */
@SpringBootTest(properties = "payment.provider=payos")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PayOsFlowIntegrationTest {

    private static final String CUSTOMER_EMAIL = "customer1@lemar.vn";
    private static final String CHECKSUM_KEY = "it-checksum";
    private static final String CHECKOUT_URL = "https://pay.payos.vn/web/it";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static HttpServer payOs;
    private static final AtomicInteger CREATE_CALLS = new AtomicInteger();
    private static final AtomicReference<String> QUERY_RESPONSE = new AtomicReference<>();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private PaymentReconciliationService reconciliation;
    @MockBean
    private NotificationService notifications;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Long invoiceId;
    private Long orderId;
    private Long unitId;
    private Long customerId;

    @BeforeAll
    static void startFakePayOs() throws IOException {
        payOs = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        payOs.createContext("/v2/payment-requests", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if ("POST".equals(exchange.getRequestMethod())) {
                CREATE_CALLS.incrementAndGet();
                respond(exchange, "{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"checkoutUrl\":\""
                        + CHECKOUT_URL + "\",\"status\":\"PENDING\"}}");
            } else {
                respond(exchange, QUERY_RESPONSE.get());
            }
        });
        payOs.start();
    }

    @AfterAll
    static void stopFakePayOs() {
        payOs.stop(0);
    }

    @DynamicPropertySource
    static void payOsProperties(DynamicPropertyRegistry registry) {
        registry.add("payos.client-id", () -> "it-client");
        registry.add("payos.api-key", () -> "it-api");
        registry.add("payos.checksum-key", () -> CHECKSUM_KEY);
        registry.add("payos.base-url", () -> "http://127.0.0.1:" + payOs.getAddress().getPort());
    }

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM order_notifications");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM payment_transactions");
        jdbc.update("DELETE FROM invoices");
        jdbc.update("DELETE FROM proposal_feedbacks");
        jdbc.update("DELETE FROM rental_orders");
        jdbc.update("DELETE FROM rental_requests");
        jdbc.update("UPDATE storage_units SET status = 'Available' WHERE status = 'Reserved'");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        CREATE_CALLS.set(0);

        customerId = jdbc.queryForObject("SELECT id FROM accounts WHERE email = ?", Long.class, CUSTOMER_EMAIL);
        unitId = jdbc.queryForObject("SELECT id FROM storage_units WHERE status = 'Available' ORDER BY id LIMIT 1",
                Long.class);
        orderId = insertOrder();
        invoiceId = insertDepositInvoice(orderId, "INV-DEP-PAYOS");
    }

    @Test
    void payThenWebhookSettlesTheDeposit() throws Exception {
        String orderCode = openSession(invoiceId);

        assertThat(orderCode).matches("\\d{15}");
        assertThat(paymentUrlOf(orderCode)).isEqualTo(CHECKOUT_URL);
        assertThat(webhook(orderCode, 10000, "00")).isEqualTo("ACCEPTED");

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Success");
        assertThat(jdbc.queryForObject("SELECT gateway_transaction_no FROM payment_transactions"
                + " WHERE vnp_txn_ref = ?", String.class, orderCode)).isEqualTo("FT-IT-001");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Paid");
        assertThat(statusOf("rental_orders", "id", orderId)).isEqualTo("Deposited");
        assertThat(statusOf("storage_units", "id", unitId)).isEqualTo("Reserved");
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs", String.class))
                .contains("PAYMENT_SUCCEEDED", "RENTAL_ORDER_DEPOSITED", "STORAGE_UNIT_RESERVED");
        assertThat(paymentUrlOf(orderCode)).as("URL bị xoá khi giao dịch chốt").isNull();
        assertThat(redis.hasKey("payment:url:" + invoiceId)).isFalse();
    }

    @Test
    void duplicateWebhookIsAcknowledgedWithoutSecondAudit() throws Exception {
        String orderCode = openSession(invoiceId);

        assertThat(webhook(orderCode, 10000, "00")).isEqualTo("ACCEPTED");
        assertThat(webhook(orderCode, 10000, "00")).isEqualTo("ALREADY_SETTLED");

        assertThat(countAudit("PAYMENT_SUCCEEDED")).isEqualTo(1);
    }

    @Test
    void webhookWithBadSignatureIsRejectedAndChangesNothing() throws Exception {
        String orderCode = openSession(invoiceId);
        Map<String, Object> data = webhookData(orderCode, 10000, "00");

        mockMvc.perform(post("/api/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(data, sign(data, "wrong-key")))))
                .andExpect(status().isBadRequest());

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Pending");
        assertThat(countAudit("PAYMENT_SUCCEEDED")).isZero();
    }

    @Test
    void webhookWithWrongAmountChangesNothing() throws Exception {
        String orderCode = openSession(invoiceId);

        assertThat(webhook(orderCode, 9000, "00")).isEqualTo("INVALID_AMOUNT");

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Pending");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Unpaid");
    }

    @Test
    void webhookWithNonSuccessCodeIsIgnored() throws Exception {
        String orderCode = openSession(invoiceId);

        assertThat(webhook(orderCode, 10000, "01")).isEqualTo("IGNORED");

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Pending");
    }

    /** confirm-webhook của payOS gửi giao dịch mẫu {@code orderCode = 123} — phải nhận 200 để lưu URL. */
    @Test
    void confirmWebhookSampleIsAcknowledged() throws Exception {
        assertThat(webhook("123", 3000, "00")).isEqualTo("NOT_FOUND");
    }

    @Test
    void cacheMissReturnsTheStoredUrlWithoutCreatingANewLink() throws Exception {
        String orderCode = openSession(invoiceId);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        mockMvc.perform(payAs(invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentUrl").value(CHECKOUT_URL));

        assertThat(CREATE_CALLS.get()).as("không tạo link payOS thứ hai").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions WHERE invoice_id = ?",
                Integer.class, invoiceId)).isEqualTo(1);
        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Pending");
    }

    @Test
    void openSessionLocksTheUnitForAnotherInvoice() throws Exception {
        openSession(invoiceId);
        Long otherOrder = insertOrder();
        Long otherInvoice = insertDepositInvoice(otherOrder, "INV-DEP-PAYOS-2");

        mockMvc.perform(payAs(otherInvoice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("đang trong quá trình xử lý thanh toán")));
    }

    @Test
    void lateSuccessOnCanceledInvoiceDoesNotDepositTheOrder() throws Exception {
        String orderCode = openSession(invoiceId);
        jdbc.update("UPDATE invoices SET status = 'Canceled' WHERE id = ?", invoiceId);

        assertThat(webhook(orderCode, 10000, "00")).isEqualTo("ACCEPTED");

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Success");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Canceled");
        assertThat(statusOf("rental_orders", "id", orderId)).isEqualTo("Pending");
        assertThat(statusOf("storage_units", "id", unitId)).isEqualTo("Available");
    }

    @Test
    void reconciliationSettlesAPaidLink() throws Exception {
        String orderCode = openSession(invoiceId);
        ageSession(orderCode, 16);
        QUERY_RESPONSE.set("{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"orderCode\":" + orderCode
                + ",\"amount\":10000,\"amountPaid\":10000,\"amountRemaining\":0,\"status\":\"PAID\","
                + "\"transactions\":[{\"reference\":\"FT-IT-002\",\"amount\":10000,"
                + "\"transactionDateTime\":\"2026-10-30 10:05:00\"}]}}");

        assertThat(reconciliation.reconcilePending()).isEqualTo(1);

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Success");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Paid");
        assertThat(statusOf("rental_orders", "id", orderId)).isEqualTo("Deposited");
        assertThat(paymentUrlOf(orderCode)).isNull();
    }

    @Test
    void reconciliationClosesAnExpiredLink() throws Exception {
        String orderCode = openSession(invoiceId);
        ageSession(orderCode, 16);
        QUERY_RESPONSE.set("{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"orderCode\":" + orderCode
                + ",\"amount\":10000,\"amountPaid\":0,\"status\":\"EXPIRED\",\"transactions\":[]}}");

        assertThat(reconciliation.reconcilePending()).isEqualTo(1);

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Failed");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Unpaid");
    }

    @Test
    void reconciliationKeepsAnUnderpaidLinkPending() throws Exception {
        String orderCode = openSession(invoiceId);
        ageSession(orderCode, 16);
        QUERY_RESPONSE.set("{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"orderCode\":" + orderCode
                + ",\"amount\":10000,\"amountPaid\":5000,\"status\":\"UNDERPAID\",\"transactions\":[]}}");

        assertThat(reconciliation.reconcilePending()).isZero();

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Pending");
    }

    @Test
    void reconciliationClosesAMissingLink() throws Exception {
        String orderCode = openSession(invoiceId);
        ageSession(orderCode, 16);
        QUERY_RESPONSE.set("{\"code\":\"101\",\"desc\":\"Mã thanh toán không tồn tại\"}");

        assertThat(reconciliation.reconcilePending()).isEqualTo(1);

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Failed");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Unpaid");
    }

    @Test
    void reconciliationKeepsPendingWhenPayOsAnswersWithAnError() throws Exception {
        String orderCode = openSession(invoiceId);
        ageSession(orderCode, 16);
        QUERY_RESPONSE.set("{\"code\":\"20\",\"desc\":\"Unauthorized\",\"data\":null}");

        assertThat(reconciliation.reconcilePending()).isZero();

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", orderCode)).isEqualTo("Pending");
    }

    // ------------------------------------------------------------------ helpers

    private String openSession(Long invoice) throws Exception {
        mockMvc.perform(payAs(invoice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentUrl").value(CHECKOUT_URL));
        return jdbc.queryForObject("SELECT vnp_txn_ref FROM payment_transactions WHERE invoice_id = ? "
                + "ORDER BY id DESC LIMIT 1", String.class, invoice);
    }

    /** Gửi webhook ký đúng checksum key; trả {@code result} của response 200. */
    private String webhook(String orderCode, long amount, String code) throws Exception {
        Map<String, Object> data = webhookData(orderCode, amount, code);
        MvcResult result = mockMvc.perform(post("/api/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(data, sign(data, CHECKSUM_KEY)))))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.result");
    }

    /** {@code data} của webhook đúng mẫu docs payOS. */
    private static Map<String, Object> webhookData(String orderCode, long amount, String code) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", Long.parseLong(orderCode));
        data.put("amount", amount);
        data.put("description", "SS0000042");
        data.put("accountNumber", "12345678");
        data.put("reference", "FT-IT-001");
        data.put("transactionDateTime", "2026-10-30 10:05:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "it-link");
        data.put("code", code);
        data.put("desc", "Thành công");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }

    private static Map<String, Object> body(Map<String, Object> data, String signature) {
        return Map.of("code", "00", "desc", "success", "success", true, "data", data, "signature", signature);
    }

    /** Chữ ký webhook như payOS: key của {@code data} theo alphabet, {@code key=value} nối bằng {@code &}. */
    private static String sign(Map<String, Object> data, String key) {
        StringBuilder query = new StringBuilder();
        new TreeMap<>(data).forEach((k, v) -> {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(k).append('=').append(v == null ? "" : v);
        });
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(query.toString().getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void ageSession(String orderCode, int minutes) {
        jdbc.update("UPDATE payment_transactions SET created_at = now() - make_interval(mins => ?) "
                + "WHERE vnp_txn_ref = ?", minutes, orderCode);
    }

    private Long insertOrder() {
        Long facilityId = jdbc.queryForObject("SELECT facility_id FROM storage_units WHERE id = ?", Long.class, unitId);
        Long unitTypeId = jdbc.queryForObject("SELECT unit_type_id FROM storage_units WHERE id = ?", Long.class,
                unitId);
        Long requestId = jdbc.queryForObject(
                "INSERT INTO rental_requests (facility_id, customer_name, normalized_customer_email,"
                        + " customer_phone, unit_type_id, start_date, period, status)"
                        + " VALUES (?, 'Nguyen Van A', ?, '0900000000', ?, CURRENT_DATE, 1, 'Converted')"
                        + " RETURNING id", Long.class, facilityId, CUSTOMER_EMAIL, unitTypeId);
        return jdbc.queryForObject(
                "INSERT INTO rental_orders (request_id, customer_id, unit_id, status, expires_at)"
                        + " VALUES (?, ?, ?, 'Pending', now() + interval '3 days') RETURNING id",
                Long.class, requestId, customerId, unitId);
    }

    private Long insertDepositInvoice(Long order, String code) {
        return jdbc.queryForObject(
                "INSERT INTO invoices (order_id, customer_id, code, title, type, status, amount, due_date)"
                        + " VALUES (?, ?, ?, 'Dat coc khoang chua', 'Deposit', 'Unpaid', 10000.00,"
                        + " now() + interval '3 days') RETURNING id", Long.class, order, customerId, code);
    }

    private MockHttpServletRequestBuilder payAs(Long invoice) {
        return post("/api/invoices/{id}/pay", invoice)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token());
    }

    private String token() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self-storage").issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("email", CUSTOMER_EMAIL).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String statusOf(String table, String column, Object value) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE " + column + " = ?", String.class, value);
    }

    private String paymentUrlOf(String orderCode) {
        return jdbc.queryForObject("SELECT payment_url FROM payment_transactions WHERE vnp_txn_ref = ?",
                String.class, orderCode);
    }

    private int countAudit(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
