package vn.lemar.selfstorage.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Hạ tầng dùng chung cho test end-to-end chạy qua HTTP thật: đăng nhập lấy JWT từ seed, gọi route như FE gọi,
 * đọc lại DB để khẳng định state.
 *
 * <p>Khác với test hiện có: không set {@code SecurityContextHolder}, không mock service — chỉ mock
 * {@link vn.lemar.selfstorage.payment.application.PaymentGateway} ở lớp con để IPN đi qua được.
 */
public abstract class HttpE2ESupport {

    protected static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Mật khẩu chung của account seed (`backend/sql/README.md`). */
    protected static final String PASSWORD = "Test@1234";
    protected static final String TMN_CODE = "TESTTMN1";

    private static final DateTimeFormatter PAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicInteger TXN = new AtomicInteger();

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected JdbcTemplate jdbc;

    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ HTTP

    /** Access token của account seed; đăng nhập một lần cho mỗi email trong một lớp test. */
    protected String bearer(String email) {
        return tokens.computeIfAbsent(email, value -> {
            ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/login",
                    Map.of("email", value, "password", PASSWORD), JsonNode.class);
            assertThat(response.getStatusCode().value()).as("đăng nhập %s", value).isEqualTo(200);
            String token = response.getBody().path("data").path("token").asText();
            assertThat(token).as("token của %s", value).isNotBlank();
            return token;
        });
    }

    protected ResponseEntity<JsonNode> get(String path, String email) {
        return exchange(path, HttpMethod.GET, email, null);
    }

    protected ResponseEntity<JsonNode> post(String path, String email, Object body) {
        return exchange(path, HttpMethod.POST, email, body);
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, String email, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (email != null) {
            headers.setBearerAuth(bearer(email));
        }
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    /** Khẳng định 2xx + envelope đúng quy ước, trả về {@code data}. */
    protected JsonNode ok(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode().value())
                .as("HTTP body: %s", response.getBody())
                .isBetween(200, 299);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("message").asText()).isNotBlank();
        return body.path("data");
    }

    // ------------------------------------------------------------------ IPN

    /**
     * Gọi IPN của VNPay như server cổng gọi vào. Chữ ký do {@code PaymentGateway} mock chấp nhận — phần chữ ký
     * thật đã có {@code VnPayGatewayTest}.
     *
     * @return {@code RspCode} của phản hồi ({@code 00} xác nhận thành công, {@code 02} đã xác nhận trước đó)
     */
    protected String ipn(String txnRef, BigDecimal amount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "E2E" + TXN.incrementAndGet() + System.nanoTime() % 100000);
        params.put("vnp_PayDate", LocalDateTime.now(VN).format(PAY_DATE));
        params.put("vnp_SecureHash", "signed-by-mock-gateway");
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/payments/vnpay/ipn");
        params.forEach(builder::queryParam);
        ResponseEntity<JsonNode> response = get(builder.build().toUriString(), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody().path("RspCode").asText();
    }

    // ------------------------------------------------------------------ DB

    protected long id(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        assertThat(value).as("query: %s", sql).isNotNull();
        return value;
    }

    protected String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    protected long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /**
     * Số bản ghi audit của một entity. Phải lọc cả {@code entity_type}: id của các bảng khác nhau trùng nhau
     * (đơn 1 và lịch hẹn 1 trong DB test mới) nên chỉ lọc {@code entity_id} là đếm nhầm.
     */
    protected long auditCount(String action, String entityType, long entityId) {
        return count("select count(*) from audit_logs where action = ? and entity_type = ? and entity_id = ?",
                action, entityType, String.valueOf(entityId));
    }

    protected long notifications(String email, String type, long orderId) {
        return count("select count(*) from notifications n"
                + " join order_notifications o on o.notification_id = n.id"
                + " join accounts a on a.id = n.account_id"
                + " where a.email = ? and n.type = ? and o.order_id = ?", email, type, orderId);
    }

    /** {@code vnp_txn_ref} của phiên thanh toán mới nhất cho hoá đơn. */
    protected String latestTxnRef(long invoiceId) {
        return text("select vnp_txn_ref from payment_transactions where invoice_id = ? order by id desc limit 1",
                invoiceId);
    }
}
