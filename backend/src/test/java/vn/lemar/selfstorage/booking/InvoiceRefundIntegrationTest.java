package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.identity.application.JwtService;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class InvoiceRefundIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String USER = "customer1@example.com";
    private static final String FS = "fs1@example.com";
    private static final String ADMIN = "admin@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AccountRepository accounts;

    private Long invoiceId;

    @BeforeEach
    void seedRefundRequiredInvoice() {
        jdbc.update("delete from order_notifications");
        jdbc.update("delete from notifications");
        jdbc.update("delete from audit_logs");
        jdbc.update("delete from refund_transactions");
        jdbc.update("delete from payment_reconciliation_cases");
        jdbc.update("delete from payment_transactions");
        jdbc.update("delete from invoices");
        jdbc.update("delete from rental_orders");
        jdbc.update("delete from rental_requests");

        Long requestId = jdbc.queryForObject("""
                insert into rental_requests (facility_id, customer_name, normalized_customer_email,
                    customer_phone, unit_type_id, start_date, period, status)
                values (1, 'Khách refund', 'customer1@example.com', '0901234567', 1, current_date, 6, 'Converted')
                returning id
                """, Long.class);
        Long orderId = jdbc.queryForObject("""
                insert into rental_orders (request_id, customer_id, unit_id, status)
                values (?, (select id from accounts where email = ?), 1, 'Deposited') returning id
                """, Long.class, requestId, USER);
        invoiceId = jdbc.queryForObject("""
                insert into invoices (order_id, customer_id, code, title, type, status, amount,
                    refund_status, refund_reason, refund_requested_at, created_at, due_date)
                values (?, (select id from accounts where email = ?), 'INV-REFUND-TEST', 'Đặt cọc',
                    'Deposit', 'Paid', 500000, 'Required', 'UNIT_UNAVAILABLE',
                    now() - interval '5 minutes', now() - interval '30 minutes', now() + interval '2 days')
                returning id
                """, Long.class, orderId, USER);
        jdbc.update("""
                insert into payment_transactions (invoice_id, vnp_txn_ref, amount, direction, status,
                    gateway_transaction_no, paid_at, created_at)
                values (?, 'TXN-REFUND-TEST', 500000, 'PAY', 'Success', 'GW-REFUND-TEST',
                    now() - interval '29 minutes', now() - interval '30 minutes')
                """, invoiceId);
    }

    @Test
    void fsHandoverCreatesLedgerAndWaitsForCustomerConfirmation() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Success"))
                .andExpect(jsonPath("$.data.method").value("CASH"))
                .andExpect(jsonPath("$.data.amount").value(500000))
                .andExpect(jsonPath("$.data.customerConfirmedAt").value(nullValue()));

        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("AwaitingConfirmation");
        assertThat(jdbc.queryForObject(
                "select count(*) from refund_transactions where invoice_id = ? and status = 'Success'",
                Integer.class, invoiceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from notifications where account_id = (select id from accounts where email = ?)",
                Integer.class, USER)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'MANUAL_REFUND_RECORDED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void fmCanHandoverUsingExistingRefundPermission() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Success"));

        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("AwaitingConfirmation");
    }

    @Test
    void refundUsesInvoiceAmountNotFullSourceAmount() throws Exception {
        jdbc.update("update invoices set amount = 200000 where id = ?", invoiceId);

        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(200000));

        assertThat(jdbc.queryForObject(
                "select amount from refund_transactions where invoice_id = ?",
                java.math.BigDecimal.class, invoiceId)).isEqualByComparingTo("200000");
    }

    @Test
    void handoverRejectsNonHttpEvidenceUrl() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CASH","evidenceUrl":"javascript:alert(1)"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPathIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/not-a-number/handover")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCannotConfirmBeforeHandover() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/confirm", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("Required");
    }

    @Test
    void customerConfirmMarksInvoiceRefundedAndRepeatsIdempotently() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/refunds/invoices/{id}/confirm", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Success"))
                .andExpect(jsonPath("$.data.customerConfirmedAt").isNotEmpty());

        mockMvc.perform(post("/api/refunds/invoices/{id}/confirm", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("Refunded");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'REFUND_CUSTOMER_CONFIRMED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void customerCannotHandoverRefund() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("Required");
    }

    @Test
    void queueMovesFromActiveToHistoryAfterCustomerConfirm() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/refunds/queues/invoices")
                        .param("view", "active")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].refundStatus").value("AwaitingConfirmation"));

        mockMvc.perform(post("/api/refunds/invoices/{id}/confirm", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/refunds/queues/invoices")
                        .param("view", "active")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/refunds/queues/invoices")
                        .param("view", "history")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].refundStatus").value("Refunded"));
    }

    @Test
    void customerCanDisputeAfterHandoverWithoutSecondRefund() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/refunds/invoices/{id}/dispute", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Chưa nhận được tiền\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("AwaitingConfirmation");
        assertThat(jdbc.queryForObject(
                "select count(*) from refund_transactions where invoice_id = ?",
                Integer.class, invoiceId)).isEqualTo(1);
        mockMvc.perform(post("/api/refunds/invoices/{id}/dispute", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"báo lại\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'REFUND_CUSTOMER_DISPUTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from notifications where type = 'REFUND_DISPUTE_RECORDED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void secondHandoverIsRejectedAfterInvoiceMovedToAwaitingConfirmation() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject(
                "select count(*) from refund_transactions where invoice_id = ?",
                Integer.class, invoiceId)).isEqualTo(1);
    }

    @Test
    void invalidRefundQueueViewIsRejected() throws Exception {
        mockMvc.perform(get("/api/refunds/queues/invoices")
                        .param("view", "unknown")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentHandoverCreatesOneRefund() throws Exception {
        String token = bearerOf(FS);

        List<Integer> statuses = runConcurrently(() -> mockMvc.perform(
                        post("/api/refunds/invoices/{id}/handover", invoiceId)
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(handoverBody()))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject(
                "select count(*) from refund_transactions where invoice_id = ?", Integer.class, invoiceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("AwaitingConfirmation");
    }

    @Test
    void concurrentConfirmIsIdempotent() throws Exception {
        handoverOnce();
        String token = bearerOf(USER);

        List<Integer> statuses = runConcurrently(() -> mockMvc.perform(
                        post("/api/refunds/invoices/{id}/confirm", invoiceId)
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'REFUND_CUSTOMER_CONFIRMED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("Refunded");
    }

    @Test
    void concurrentDisputeIsIdempotent() throws Exception {
        handoverOnce();
        String token = bearerOf(USER);

        List<Integer> statuses = runConcurrently(() -> mockMvc.perform(
                        post("/api/refunds/invoices/{id}/dispute", invoiceId)
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Chua nhan duoc tien\"}"))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'REFUND_CUSTOMER_DISPUTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from notifications where type = 'REFUND_DISPUTE_RECORDED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("AwaitingConfirmation");
    }

    @Test
    void otherAccountCannotConfirmOrDispute() throws Exception {
        handoverOnce();
        String other = bearerOf(FS);

        mockMvc.perform(post("/api/refunds/invoices/{id}/confirm", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/refunds/invoices/{id}/dispute", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Khong phai chu\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                "select refund_status from invoices where id = ?", String.class, invoiceId))
                .isEqualTo("AwaitingConfirmation");
    }

    @Test
    void handoverRejectsAmountAboveSource() throws Exception {
        jdbc.update("update invoices set amount = 600000 where id = ?", invoiceId);

        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handoverRejectsSourceFromAnotherInvoice() throws Exception {
        Long otherSourceId = seedOtherInvoiceWithPayment();

        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CASH","evidenceUrl":"https://example.com/receipt.jpg",
                                 "sourceTransactionId":%d}
                                """.formatted(otherSourceId)))
                .andExpect(status().isBadRequest());
    }

    private void handoverOnce() throws Exception {
        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(FS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoverBody()))
                .andExpect(status().isOk());
    }

    private List<Integer> runConcurrently(Callable<Integer> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> gated = () -> {
            ready.countDown();
            start.await();
            return task.call();
        };
        Future<Integer> first = pool.submit(gated);
        Future<Integer> second = pool.submit(gated);
        try {
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent tasks did not become ready");
            }
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private Long seedOtherInvoiceWithPayment() {
        Long requestId = jdbc.queryForObject("""
                insert into rental_requests (facility_id, customer_name, normalized_customer_email,
                    customer_phone, unit_type_id, start_date, period, status)
                values (1, 'Khách khác', 'customer1@example.com', '0901234567', 1, current_date, 6, 'Converted')
                returning id
                """, Long.class);
        Long otherOrderId = jdbc.queryForObject("""
                insert into rental_orders (request_id, customer_id, unit_id, status)
                values (?, (select id from accounts where email = 'customer1@example.com'), 2, 'Deposited')
                returning id
                """, Long.class, requestId);
        Long otherInvoiceId = jdbc.queryForObject("""
                insert into invoices (order_id, customer_id, code, title, type, status, amount,
                    created_at, due_date)
                values (?, (select id from accounts where email = 'customer1@example.com'),
                    'INV-OTHER-SOURCE', 'Đặt cọc', 'Deposit', 'Paid', 500000,
                    now() - interval '1 hour', now() + interval '2 days')
                returning id
                """, Long.class, otherOrderId);
        jdbc.update("""
                insert into payment_transactions (invoice_id, vnp_txn_ref, amount, direction, status,
                    gateway_transaction_no, paid_at, created_at)
                values (?, 'TXN-OTHER-SOURCE', 500000, 'PAY', 'Success', 'GW-OTHER-SOURCE',
                    now(), now())
                """, otherInvoiceId);
        return jdbc.queryForObject(
                "select id from payment_transactions where vnp_txn_ref = 'TXN-OTHER-SOURCE'", Long.class);
    }

    private static String handoverBody() {
        return """
                {"method":"CASH","evidenceUrl":"https://example.com/receipt.jpg","note":"Đã đưa tiền"}
                """;
    }

    private String bearerOf(String email) {
        Account account = accounts.findByEmail(email).orElseThrow();
        return "Bearer " + jwtService.issueAccessToken(account);
    }
}
