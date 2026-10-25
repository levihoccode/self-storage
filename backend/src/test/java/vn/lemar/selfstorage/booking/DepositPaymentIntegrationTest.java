package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Toàn tuyến đặt cọc trên Postgres thật: gọi endpoint → mở phiên → VNPay xác nhận qua IPN →
 * hóa đơn, đơn, khoang và audit cùng đổi.
 *
 * <p>Test <strong>không</strong> đánh dấu {@code @Transactional}: listener nhánh success chạy
 * {@code AFTER_COMMIT}, nên nếu transaction của test không commit thật thì nó không bao giờ chạy
 * và bài test trở nên vô nghĩa. Dữ liệu dọn tay ở {@link #resetData()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DepositPaymentIntegrationTest {

    private static final String CUSTOMER_EMAIL = "customer1@lemar.vn";
    private static final String TMN_CODE = "TESTTMN1";
    private static final String HASH_SECRET = "0123456789abcdef0123456789abcdef";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private vn.lemar.selfstorage.notification.application.NotificationService notifications;

    private Long invoiceId;
    private Long orderId;
    private Long unitId;

    @BeforeEach
    void resetData() {
        // Thứ tự theo chiều khoá ngoại: notification trỏ vào order nên phải xoá trước.
        jdbc.update("DELETE FROM order_notifications");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM payment_transactions");
        jdbc.update("DELETE FROM invoices");
        jdbc.update("DELETE FROM rental_orders");
        jdbc.update("DELETE FROM rental_requests");
        jdbc.update("UPDATE storage_units SET status = 'Available' WHERE status = 'Reserved'");

        Long customerId = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE email = ?", Long.class, CUSTOMER_EMAIL);
        unitId = jdbc.queryForObject(
                "SELECT id FROM storage_units WHERE status = 'Available' ORDER BY id LIMIT 1",
                Long.class);
        Long facilityId = jdbc.queryForObject(
                "SELECT facility_id FROM storage_units WHERE id = ?", Long.class, unitId);
        Long unitTypeId = jdbc.queryForObject("SELECT id FROM unit_types ORDER BY id LIMIT 1", Long.class);

        Long requestId = jdbc.queryForObject(
                "INSERT INTO rental_requests (facility_id, customer_name, normalized_customer_email,"
                        + " customer_phone, unit_type_id, start_date, period, status)"
                        + " VALUES (?, 'Nguyen Van A', ?, '0900000000', ?, CURRENT_DATE, 1, 'Approved')"
                        + " RETURNING id",
                Long.class, facilityId, CUSTOMER_EMAIL, unitTypeId);
        orderId = jdbc.queryForObject(
                "INSERT INTO rental_orders (request_id, customer_id, unit_id, status, expires_at)"
                        + " VALUES (?, ?, ?, 'Pending', now() + interval '3 days') RETURNING id",
                Long.class, requestId, customerId, unitId);
        invoiceId = jdbc.queryForObject(
                "INSERT INTO invoices (order_id, customer_id, code, title, type, status, amount, due_date)"
                        + " VALUES (?, ?, 'INV-DEP-IT', 'Dat coc khoang chua', 'Deposit', 'Unpaid',"
                        + " 10000.00, now() + interval '3 days') RETURNING id",
                Long.class, orderId, customerId);
    }

    @Test
    void depositFlowFromEndpointThroughIpnUpdatesEverything() throws Exception {
        java.time.Instant deadlineBeforePaying = jdbc.queryForObject(
                "SELECT expires_at FROM rental_orders WHERE id = ?", java.time.Instant.class, orderId);
        String txnRef = openSessionAndReadTxnRef();

        // Phiên mở ra phải để lại dấu vết đối soát trước khi khách rời đi.
        assertThat(jdbc.queryForObject(
                "SELECT status FROM payment_transactions WHERE vnp_txn_ref = ?", String.class, txnRef))
                .isEqualTo("Pending");

        confirmByIpn(txnRef);

        assertThat(statusOf("invoices", invoiceId)).isEqualTo("Paid");
        assertThat(statusOf("rental_orders", orderId)).isEqualTo("Deposited");
        assertThat(statusOf("storage_units", unitId)).isEqualTo("Reserved");
        // Spec `db-table-draft.md` › RentalOrder: hạn đơn "set một lần khi tạo đơn"; › StorageUnit:
        // hạn giữ kho là phần còn lại của chính hạn đó. Cọc xong không được gia hạn thêm.
        assertThat(jdbc.queryForObject(
                "SELECT expires_at FROM rental_orders WHERE id = ?", java.time.Instant.class, orderId))
                .as("cọc không được dời hạn đơn").isEqualTo(deadlineBeforePaying);
    }

    @Test
    void successWritesThreeAuditEntries() throws Exception {
        confirmByIpn(openSessionAndReadTxnRef());

        assertThat(jdbc.queryForList("SELECT action FROM audit_logs ORDER BY action", String.class))
                .containsExactly("PAYMENT_SUCCEEDED", "RENTAL_ORDER_DEPOSITED", "STORAGE_UNIT_RESERVED");
    }

    /**
     * Khách bấm back rồi bấm lại hóa đơn của chính mình: phiên cũ không được coi là "khách khác"
     * (review #137 mục 6, chốt phương án A).
     */
    @Test
    void retryingTheSameInvoiceIsNotBlocked() throws Exception {
        openSessionAndReadTxnRef();

        mockMvc.perform(payAs(CUSTOMER_EMAIL)).andExpect(status().isOk());
    }

    @Test
    void reservedUnitVoidsTheInvoiceAndRefuses() throws Exception {
        jdbc.update("UPDATE storage_units SET status = 'Reserved' WHERE id = ?", unitId);

        mockMvc.perform(payAs(CUSTOMER_EMAIL))
                .andExpect(status().isConflict());

        assertThat(statusOf("invoices", invoiceId)).isEqualTo("Canceled");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'INVOICE_CANCELED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void expiredOrderIsRefusedWithoutVoidingTheInvoice() throws Exception {
        jdbc.update("UPDATE rental_orders SET expires_at = now() - interval '1 hour' WHERE id = ?", orderId);

        mockMvc.perform(payAs(CUSTOMER_EMAIL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Đơn thuê kho đã hết hạn")));

        assertThat(statusOf("invoices", invoiceId)).isEqualTo("Unpaid");
    }

    @Test
    void someoneElsesInvoiceIsNotFound() throws Exception {
        mockMvc.perform(payAs("fm1@lemar.vn"))
                .andExpect(status().isNotFound());
    }

    /**
     * Listener chạy trong một transaction riêng (REQUIRES_NEW): hỏng giữa chừng thì cả bốn cập
     * nhật phải quay lại, không được để hóa đơn `Paid` mà khoang vẫn `Available`.
     */
    @Test
    void aFailureInsideTheListenerRollsBackEveryUpdate() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("gửi thông báo hỏng"))
                .when(notifications).notify(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.any());

        confirmByIpn(openSessionAndReadTxnRef());

        assertThat(statusOf("invoices", invoiceId)).isEqualTo("Unpaid");
        assertThat(statusOf("rental_orders", orderId)).isEqualTo("Pending");
        assertThat(statusOf("storage_units", unitId)).isEqualTo("Available");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action <> 'PAYMENT_SUCCEEDED'",
                Integer.class))
                .as("audit của booking phải rollback theo").isZero();
    }

    /** Token thật do JwtEncoder của app ký — đi qua đúng đường xác thực mà request thường đi. */
    private String tokenFor(String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self-storage").issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("email", email).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder payAs(String email) {
        return post("/api/invoices/{id}/pay", invoiceId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(email));
    }

    private String openSessionAndReadTxnRef() throws Exception {
        MvcResult result = mockMvc.perform(payAs(CUSTOMER_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentUrl").exists())
                .andReturn();

        String paymentUrl = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.paymentUrl");
        return queryParam(paymentUrl, "vnp_TxnRef");
    }

    /** Dựng IPN có chữ ký thật bằng hash secret của test rồi gọi vào đúng endpoint VNPay dùng. */
    private void confirmByIpn(String txnRef) throws Exception {
        Map<String, String> params = new TreeMap<>(Map.of(
                "vnp_Amount", "1000000",
                "vnp_BankCode", "NCB",
                "vnp_OrderInfo", "Dat coc khoang chua",
                "vnp_PayDate", "20261013161500",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_TmnCode", TMN_CODE,
                "vnp_TransactionNo", "14777001",
                "vnp_TxnRef", txnRef));

        var request = get("/api/payments/vnpay/ipn");
        params.forEach(request::param);
        request.param("vnp_SecureHash", sign(params));

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
    }

    private static String sign(Map<String, String> params) throws Exception {
        StringBuilder data = new StringBuilder();
        new TreeMap<>(params).forEach((key, value) -> {
            if (value == null || value.isEmpty()) {
                return;
            }
            if (data.length() > 0) {
                data.append('&');
            }
            data.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        });
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(HASH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal(data.toString().getBytes(StandardCharsets.US_ASCII))) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String queryParam(String url, String name) {
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.US_ASCII);
            }
        }
        throw new IllegalStateException("URL thanh toán thiếu " + name);
    }

    private String statusOf(String table, Long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }
}
