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
import vn.lemar.selfstorage.payment.application.PaymentService;

/**
 * Luồng đặt cọc qua ZaloPay trên Postgres + Redis thật: {@code POST /api/invoices/{id}/pay} → callback
 * → hoá đơn/đơn/khoang/audit, cùng các nhánh callback lặp/sai, khoá khoang, resume khi mất cache, tiền
 * về muộn và job đối soát qua {@code /v2/query}. Cổng ZaloPay ({@code /v2/create}, {@code /v2/query})
 * được thay bằng server HTTP cục bộ; chữ ký callback ký bằng key2 của test như cổng thật.
 */
@SpringBootTest(properties = "payment.provider=zalopay")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ZaloPayFlowIntegrationTest {

    private static final String CUSTOMER_EMAIL = "customer1@lemar.vn";
    private static final String APP_ID = "553";
    private static final String KEY1 = "it-key1";
    private static final String KEY2 = "it-key2";
    private static final String ORDER_URL = "https://sb-gateway.zalopay.vn/openinapp?order=it";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static HttpServer zaloPay;
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
    @Autowired
    private PaymentService payments;
    @MockBean
    private NotificationService notifications;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Long invoiceId;
    private Long orderId;
    private Long unitId;
    private Long customerId;

    @BeforeAll
    static void startFakeZaloPay() throws IOException {
        zaloPay = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        zaloPay.createContext("/v2/create", exchange -> {
            CREATE_CALLS.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            respond(exchange, "{\"return_code\":1,\"return_message\":\"Giao dịch thành công\",\"order_url\":\""
                    + ORDER_URL + "\"}");
        });
        zaloPay.createContext("/v2/query", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, QUERY_RESPONSE.get());
        });
        zaloPay.start();
    }

    @AfterAll
    static void stopFakeZaloPay() {
        zaloPay.stop(0);
    }

    @DynamicPropertySource
    static void zaloPayProperties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + zaloPay.getAddress().getPort();
        registry.add("zalopay.app-id", () -> APP_ID);
        registry.add("zalopay.key1", () -> KEY1);
        registry.add("zalopay.key2", () -> KEY2);
        registry.add("zalopay.create-url", () -> base + "/v2/create");
        registry.add("zalopay.query-url", () -> base + "/v2/query");
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
        invoiceId = insertDepositInvoice(orderId, "INV-DEP-ZP");
    }

    @Test
    void payThenCallbackSettlesTheDeposit() throws Exception {
        String txnRef = openSession(invoiceId);

        assertThat(txnRef).matches("\\d{6}_INV\\d+T\\d{14}[A-Z2-9]{5}");
        assertThat(paymentUrlOf(txnRef)).isEqualTo(ORDER_URL);
        assertThat(callback(txnRef, 10000)).isEqualTo(1);

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Success");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Paid");
        assertThat(statusOf("rental_orders", "id", orderId)).isEqualTo("Deposited");
        assertThat(statusOf("storage_units", "id", unitId)).isEqualTo("Reserved");
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs", String.class))
                .contains("PAYMENT_SUCCEEDED", "RENTAL_ORDER_DEPOSITED", "STORAGE_UNIT_RESERVED");
        assertThat(paymentUrlOf(txnRef)).as("URL bị xoá khi giao dịch chốt").isNull();
        assertThat(redis.hasKey("payment:url:" + invoiceId)).isFalse();
    }

    @Test
    void duplicateCallbackIsAcknowledgedWithoutSecondAudit() throws Exception {
        String txnRef = openSession(invoiceId);

        assertThat(callback(txnRef, 10000)).isEqualTo(1);
        assertThat(callback(txnRef, 10000)).isEqualTo(1);

        assertThat(countAudit("PAYMENT_SUCCEEDED")).isEqualTo(1);
    }

    @Test
    void callbackWithBadSignatureIsRejectedAndChangesNothing() throws Exception {
        String txnRef = openSession(invoiceId);
        String data = callbackData(txnRef, 10000);

        assertThat(postCallback(data, ZaloPayTestSigner.hmac(data, "wrong-key"))).isEqualTo(2);
        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Pending");
        assertThat(countAudit("PAYMENT_SUCCEEDED")).isZero();
    }

    @Test
    void callbackWithWrongAmountIsRejectedAndChangesNothing() throws Exception {
        String txnRef = openSession(invoiceId);

        assertThat(callback(txnRef, 9000)).isEqualTo(2);
        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Pending");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Unpaid");
    }

    @Test
    void callbackForUnknownTransactionAsksForRetry() throws Exception {
        assertThat(callback("261029_UNKNOWN", 10000)).isEqualTo(0);
    }

    @Test
    void cacheMissReturnsTheStoredUrlWithoutCreatingANewOrder() throws Exception {
        String txnRef = openSession(invoiceId);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        mockMvc.perform(payAs(invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentUrl").value(ORDER_URL));

        assertThat(CREATE_CALLS.get()).as("không tạo đơn ZaloPay thứ hai").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions WHERE invoice_id = ?",
                Integer.class, invoiceId)).isEqualTo(1);
        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Pending");
    }

    @Test
    void openSessionLocksTheUnitForAnotherInvoice() throws Exception {
        openSession(invoiceId);
        Long otherOrder = insertOrder();
        Long otherInvoice = insertDepositInvoice(otherOrder, "INV-DEP-ZP-2");

        mockMvc.perform(payAs(otherInvoice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("đang trong quá trình xử lý thanh toán")));
    }

    @Test
    void payIsRefusedWhenPaymentSucceededButInvoiceIsStillUnpaid() throws Exception {
        String txnRef = openSession(invoiceId);
        // Callback đã ghi Success nhưng listener chưa chạy/lỗi: hoá đơn còn Unpaid.
        jdbc.update("UPDATE payment_transactions SET status = 'Success' WHERE vnp_txn_ref = ?", txnRef);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        mockMvc.perform(payAs(invoiceId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("đã được thanh toán thành công")));
    }

    @Test
    void lateSuccessOnCanceledInvoiceDoesNotDepositTheOrder() throws Exception {
        String txnRef = openSession(invoiceId);
        jdbc.update("UPDATE invoices SET status = 'Canceled' WHERE id = ?", invoiceId);

        assertThat(callback(txnRef, 10000)).isEqualTo(1);

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Success");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Canceled");
        assertThat(statusOf("rental_orders", "id", orderId)).isEqualTo("Pending");
        assertThat(statusOf("storage_units", "id", unitId)).isEqualTo("Available");
    }

    @Test
    void reconciliationSettlesAPaidOrderFromQuery() throws Exception {
        String txnRef = openSession(invoiceId);
        ageSession(txnRef, 16);
        QUERY_RESPONSE.set("{\"return_code\":1,\"return_message\":\"Giao dịch thành công\",\"amount\":10000,"
                + "\"zp_trans_id\":240313000000999,\"server_time\":1793252802000}");

        assertThat(reconciliation.reconcilePending()).isEqualTo(1);

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Success");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Paid");
        assertThat(statusOf("rental_orders", "id", orderId)).isEqualTo("Deposited");
        assertThat(paymentUrlOf(txnRef)).isNull();
    }

    @Test
    void reconciliationClosesAFailedOrder() throws Exception {
        String txnRef = openSession(invoiceId);
        ageSession(txnRef, 16);
        QUERY_RESPONSE.set("{\"return_code\":2,\"sub_return_code\":-54,\"return_message\":\"Giao dịch thất bại\"}");

        assertThat(reconciliation.reconcilePending()).isEqualTo(1);

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Failed");
        assertThat(statusOf("invoices", "id", invoiceId)).isEqualTo("Unpaid");
        assertThat(paymentUrlOf(txnRef)).isNull();
    }

    @Test
    void reconciliationClosesAnUnknownOrder() throws Exception {
        String txnRef = openSession(invoiceId);
        ageSession(txnRef, 16);
        QUERY_RESPONSE.set("{\"return_code\":2,\"sub_return_code\":-101,\"return_message\":\"Không tồn tại\"}");

        assertThat(reconciliation.reconcilePending()).isEqualTo(1);

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Failed");
    }

    @Test
    void reconciliationKeepsAProcessingOrderPending() throws Exception {
        String txnRef = openSession(invoiceId);
        ageSession(txnRef, 16);
        QUERY_RESPONSE.set("{\"return_code\":3,\"is_processing\":true}");

        assertThat(reconciliation.reconcilePending()).isZero();

        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Pending");
    }

    @Test
    void expiredSessionUrlIsCleared() throws Exception {
        String txnRef = openSession(invoiceId);
        ageSession(txnRef, 16);

        assertThat(payments.forgetExpiredPaymentUrls()).isEqualTo(1);

        assertThat(paymentUrlOf(txnRef)).isNull();
        assertThat(statusOf("payment_transactions", "vnp_txn_ref", txnRef)).isEqualTo("Pending");
    }

    // ------------------------------------------------------------------ helpers

    private String openSession(Long invoice) throws Exception {
        mockMvc.perform(payAs(invoice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentUrl").value(ORDER_URL));
        return jdbc.queryForObject("SELECT vnp_txn_ref FROM payment_transactions WHERE invoice_id = ? "
                + "ORDER BY id DESC LIMIT 1", String.class, invoice);
    }

    private int callback(String txnRef, long amount) throws Exception {
        String data = callbackData(txnRef, amount);
        return postCallback(data, ZaloPayTestSigner.hmac(data, KEY2));
    }

    private int postCallback(String data, String mac) throws Exception {
        Map<String, Object> body = Map.of("data", data, "mac", mac, "type", 1);
        MvcResult result = mockMvc.perform(post("/api/payments/zalopay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.return_code");
    }

    /** {@code data} của callback đúng mẫu docs ZaloPay — không có field trạng thái. */
    private String callbackData(String txnRef, long amount) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_id", Integer.parseInt(APP_ID));
        data.put("app_trans_id", txnRef);
        data.put("app_time", Instant.now().toEpochMilli());
        data.put("app_user", "self-storage");
        data.put("amount", amount);
        data.put("embed_data", "{}");
        data.put("item", "[]");
        data.put("zp_trans_id", 240313000000123L);
        data.put("server_time", Instant.now().toEpochMilli());
        data.put("channel", 38);
        data.put("merchant_user_id", "it-user");
        data.put("user_fee_amount", 0);
        data.put("discount_amount", 0);
        return objectMapper.writeValueAsString(data);
    }

    private void ageSession(String txnRef, int minutes) {
        jdbc.update("UPDATE payment_transactions SET created_at = now() - make_interval(mins => ?) "
                + "WHERE vnp_txn_ref = ?", minutes, txnRef);
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

    private String paymentUrlOf(String txnRef) {
        return jdbc.queryForObject("SELECT payment_url FROM payment_transactions WHERE vnp_txn_ref = ?",
                String.class, txnRef);
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

    /** HMAC-SHA256 hex như ZaloPay dùng cho {@code mac}. */
    static final class ZaloPayTestSigner {

        private ZaloPayTestSigner() {
        }

        static String hmac(String data, String key) {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                StringBuilder hex = new StringBuilder();
                for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
