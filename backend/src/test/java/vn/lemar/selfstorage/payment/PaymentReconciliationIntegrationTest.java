package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentReconciliationService;
import vn.lemar.selfstorage.payment.application.dto.QueryDrResult;

/** Job đối soát trên Postgres thật; querydr mock vì sandbox đang trả 99. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PaymentReconciliationIntegrationTest {

    private static final String CUSTOMER_EMAIL = "customer1@lemar.vn";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private PaymentGateway gateway;

    @MockBean
    private NotificationService notifications;

    @Autowired
    private PaymentReconciliationService reconciliation;

    @Autowired
    private JdbcTemplate jdbc;

    private Long invoiceId;
    private Long orderId;
    private Long unitId;
    private Long transactionId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM order_notifications");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM payment_transactions");
        jdbc.update("DELETE FROM invoices");
        jdbc.update("DELETE FROM proposal_feedbacks");
        jdbc.update("DELETE FROM rental_orders");
        jdbc.update("DELETE FROM rental_requests");
        jdbc.update("UPDATE storage_units SET status = 'Available' WHERE status <> 'Available'");

        Long customerId = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE email = ?", Long.class, CUSTOMER_EMAIL);
        unitId = jdbc.queryForObject(
                "SELECT id FROM storage_units WHERE status = 'Available' ORDER BY id LIMIT 1", Long.class);
        Long facilityId = jdbc.queryForObject(
                "SELECT facility_id FROM storage_units WHERE id = ?", Long.class, unitId);
        Long unitTypeId = jdbc.queryForObject("SELECT id FROM unit_types ORDER BY id LIMIT 1", Long.class);

        Long requestId = jdbc.queryForObject(
                "INSERT INTO rental_requests (facility_id, customer_name, normalized_customer_email,"
                        + " customer_phone, unit_type_id, start_date, period, status)"
                        + " VALUES (?, 'Reconcile', ?, '0900000000', ?, CURRENT_DATE, 1, 'Approved')"
                        + " RETURNING id",
                Long.class, facilityId, CUSTOMER_EMAIL, unitTypeId);
        orderId = jdbc.queryForObject(
                "INSERT INTO rental_orders (request_id, customer_id, unit_id, status, expires_at)"
                        + " VALUES (?, ?, ?, 'Pending', now() + interval '3 days') RETURNING id",
                Long.class, requestId, customerId, unitId);
        invoiceId = jdbc.queryForObject(
                "INSERT INTO invoices (order_id, customer_id, code, title, type, status, amount, due_date)"
                        + " VALUES (?, ?, 'INV-RECON-1', 'Dat coc', 'Deposit', 'Unpaid',"
                        + " 10000.00, now() + interval '3 days') RETURNING id",
                Long.class, orderId, customerId);
        transactionId = jdbc.queryForObject(
                "INSERT INTO payment_transactions (invoice_id, vnp_txn_ref, amount, direction, status,"
                        + " created_at, transaction_content)"
                        + " VALUES (?, 'INV-RECON-TXN', 10000.00, 'PAY', 'Pending',"
                        + " now() - interval '16 minutes', 'Dat coc') RETURNING id",
                Long.class, invoiceId);
    }

    @Test
    void successIsAppliedThroughTheBookingListener() {
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult("00", "00", "GW-RECON",
                Instant.now(), new BigDecimal("10000"), "ok", "{}"));

        assertThat(reconciliation.reconcilePending()).isEqualTo(1);

        assertThat(statusOf("payment_transactions", transactionId)).isEqualTo("Success");
        assertThat(statusOf("invoices", invoiceId)).isEqualTo("Paid");
        assertThat(statusOf("rental_orders", orderId)).isEqualTo("Deposited");
        assertThat(statusOf("storage_units", unitId)).isEqualTo("Reserved");
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs ORDER BY action", String.class))
                .contains("PAYMENT_SUCCEEDED", "RENTAL_ORDER_DEPOSITED", "STORAGE_UNIT_RESERVED");
    }

    @Test
    void failedStatusClosesTheTransactionWithoutTouchingBooking() {
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult("00", "02", "GW-RECON",
                null, new BigDecimal("10000"), "failed", "{}"));

        assertThat(reconciliation.reconcilePending()).isEqualTo(1);

        assertThat(statusOf("payment_transactions", transactionId)).isEqualTo("Failed");
        assertThat(statusOf("invoices", invoiceId)).isEqualTo("Unpaid");
        assertThat(statusOf("rental_orders", orderId)).isEqualTo("Pending");
        assertThat(statusOf("storage_units", unitId)).isEqualTo("Available");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'PAYMENT_FAILED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void notFoundClosesTheTransaction() {
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult("91", null, null,
                null, null, "not found", "{}"));

        assertThat(reconciliation.reconcilePending()).isEqualTo(1);

        assertThat(statusOf("payment_transactions", transactionId)).isEqualTo("Failed");
        assertThat(statusOf("invoices", invoiceId)).isEqualTo("Unpaid");
    }

    @Test
    void apiErrorLeavesTheTransactionPending() {
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult("99", null, null,
                null, null, "Unexpected error", "{}"));

        assertThat(reconciliation.reconcilePending()).isZero();

        assertThat(statusOf("payment_transactions", transactionId)).isEqualTo("Pending");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs", Integer.class)).isZero();
    }

    private String statusOf(String table, Long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }
}
