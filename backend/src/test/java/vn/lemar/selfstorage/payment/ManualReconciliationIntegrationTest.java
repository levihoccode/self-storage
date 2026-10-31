package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import vn.lemar.selfstorage.notification.application.NotificationService;

/**
 * Lớp đối soát thủ công: ops tra portal VNPay rồi nhập kết quả cho giao dịch Pending.
 *
 * <p>Test đi qua đúng endpoint ADMIN, khoá transaction row, cập nhật invoice/order/unit qua event
 * và ghi audit. Không dùng querydr — đây là lớp 3 của chiến lược IPN + local repair + manual portal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ManualReconciliationIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@lemar.vn";
    private static final String CUSTOMER_EMAIL = "customer1@lemar.vn";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private NotificationService notifications;

    private Long invoiceId;
    private Long orderId;
    private Long unitId;

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
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

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
                        + " VALUES (?, ?, 'INV-MANUAL', 'Dat coc khoang chua', 'Deposit', 'Unpaid',"
                        + " 10000.00, now() + interval '3 days') RETURNING id",
                Long.class, orderId, customerId);
    }

    @Test
    void manualSuccessFinishesTheInvoiceLikeAnIpn() throws Exception {
        String txnRef = createPendingPayment("INV-MANUAL-SUCCESS");

        mockMvc.perform(reconcileAs(ADMIN_EMAIL, txnRef, """
                        {"outcome":"SUCCESS","note":"portal: vnp_TransactionNo 14777001",
                         "paidAt":"2026-10-30T10:00:00Z","gatewayTransactionNo":"14777001"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.txnRef").value(txnRef))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.gatewayTransactionNo").value("14777001"));

        assertThat(paymentStatus(txnRef)).isEqualTo("Success");
        assertThat(statusOf("invoices", invoiceId)).isEqualTo("Paid");
        assertThat(statusOf("rental_orders", orderId)).isEqualTo("Deposited");
        assertThat(statusOf("storage_units", unitId)).isEqualTo("Reserved");
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs ORDER BY action", String.class))
                .containsExactly("PAYMENT_SUCCEEDED", "RENTAL_ORDER_DEPOSITED", "STORAGE_UNIT_RESERVED");
    }

    @Test
    void manualFailureClosesThePaymentWithoutTouchingInvoiceState() throws Exception {
        String txnRef = createPendingPayment("INV-MANUAL-FAILED");

        mockMvc.perform(reconcileAs(ADMIN_EMAIL, txnRef, """
                        {"outcome":"FAILED","note":"portal: giao dịch không thành công"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        assertThat(paymentStatus(txnRef)).isEqualTo("Failed");
        assertThat(jdbc.queryForObject(
                "SELECT failure_reason FROM payment_transactions WHERE vnp_txn_ref = ?",
                String.class, txnRef)).contains("manual:");
        assertThat(statusOf("invoices", invoiceId)).isEqualTo("Unpaid");
        assertThat(statusOf("rental_orders", orderId)).isEqualTo("Pending");
        assertThat(statusOf("storage_units", unitId)).isEqualTo("Available");
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs ORDER BY action", String.class))
                .containsExactly("PAYMENT_FAILED");
    }

    @Test
    void alreadySettledTransactionIsAConflict() throws Exception {
        String txnRef = "INV-MANUAL-ALREADY";
        jdbc.update("INSERT INTO payment_transactions"
                + " (invoice_id, vnp_txn_ref, amount, direction, status, paid_at, gateway_transaction_no)"
                + " VALUES (?, ?, 10000.00, 'PAY', 'Success', now(), 'GW-EXISTING')", invoiceId, txnRef);

        mockMvc.perform(reconcileAs(ADMIN_EMAIL, txnRef, """
                        {"outcome":"SUCCESS","note":"portal: already paid"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("đã ở trạng thái")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class)).isZero();
    }

    @Test
    void unknownTransactionIsNotFound() throws Exception {
        mockMvc.perform(reconcileAs(ADMIN_EMAIL, "INV-MANUAL-MISSING", """
                        {"outcome":"FAILED","note":"portal: not found"}
                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCannotReconcile() throws Exception {
        String txnRef = createPendingPayment("INV-MANUAL-CUSTOMER");

        mockMvc.perform(reconcileAs(CUSTOMER_EMAIL, txnRef, """
                        {"outcome":"SUCCESS","note":"self service"}
                        """))
                .andExpect(status().isForbidden());

        assertThat(paymentStatus(txnRef)).isEqualTo("Pending");
    }

    @Test
    void missingNoteIsRejected() throws Exception {
        String txnRef = createPendingPayment("INV-MANUAL-INVALID");

        mockMvc.perform(reconcileAs(ADMIN_EMAIL, txnRef, """
                        {"outcome":"SUCCESS"}
                        """))
                .andExpect(status().isBadRequest());

        assertThat(paymentStatus(txnRef)).isEqualTo("Pending");
    }

    private String createPendingPayment(String txnRef) {
        jdbc.update("INSERT INTO payment_transactions"
                + " (invoice_id, vnp_txn_ref, amount, direction, status)"
                + " VALUES (?, ?, 10000.00, 'PAY', 'Pending')", invoiceId, txnRef);
        return txnRef;
    }

    private String paymentStatus(String txnRef) {
        return jdbc.queryForObject(
                "SELECT status FROM payment_transactions WHERE vnp_txn_ref = ?", String.class, txnRef);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reconcileAs(
            String email, String txnRef, String body) {
        return post("/api/admin/payments/{txnRef}/reconcile", txnRef)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String tokenFor(String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self-storage").issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("email", email).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String statusOf(String table, Long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }
}
