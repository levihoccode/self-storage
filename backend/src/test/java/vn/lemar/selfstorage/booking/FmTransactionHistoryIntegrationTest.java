package vn.lemar.selfstorage.booking;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.identity.application.JwtService;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

/**
 * Lịch sử giao dịch FM chạy thật trên Postgres + seed: union invoice/fee/payment/refund, filter,
 * pagination, facility scope và các nhánh lỗi 400/403/404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class FmTransactionHistoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AccountRepository accountRepository;

    private long q7;
    private long td;
    private long customer1;

    @BeforeEach
    void loadSeed() {
        q7 = facilityId("Q7");
        td = facilityId("TD");
        customer1 = accountId("customer1@lemar.vn");
    }

    @Test
    void historyReturnsInvoiceFeePaymentAndRefundNewestFirst() throws Exception {
        long order = insertOrder(q7, unitId("A-01"), customer1);
        long invoice = insertInvoice(order, customer1, "INV-TX-DEPOSIT", "Đặt cọc", "Deposit",
                "Paid", "500000", "4 hours");
        long payment = insertPayment(invoice, "TXN-TX-1", "Success", "500000", "3 hours 30 minutes",
                "3 hours 30 minutes");
        insertRefund(invoice, payment, "Success", "500000", "1 hour 50 minutes",
                "1 hour 45 minutes");
        jdbc.update("update refund_transactions set disputed_at = now() - interval '1 hour', "
                + "dispute_note = 'Chưa nhận tiền' where invoice_id = ?", invoice);
        jdbc.update("update invoices set refund_status = 'AwaitingConfirmation' where id = ?", invoice);
        insertInvoice(order, customer1, "INV-TX-PENALTY", "Phí vệ sinh", "Penalty",
                "Unpaid", "100000", "1 hour");

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy lịch sử giao dịch cơ sở thành công"))
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.items[0].kind").value("FEE"))
                .andExpect(jsonPath("$.data.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.data.items[0].amount").value(100000))
                .andExpect(jsonPath("$.data.items[1].kind").value("REFUND"))
                .andExpect(jsonPath("$.data.items[1].direction").value("OUT"))
                .andExpect(jsonPath("$.data.items[1].status").value("Success"))
                .andExpect(jsonPath("$.data.items[1].method").value("CASH"))
                .andExpect(jsonPath("$.data.items[1].invoiceCode").value("INV-TX-DEPOSIT"))
                .andExpect(jsonPath("$.data.items[1].refundTransactionId").exists())
                .andExpect(jsonPath("$.data.items[1].refundConfirmedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].refundDisputedAt").exists())
                .andExpect(jsonPath("$.data.items[1].refundDisputeNote").value("Chưa nhận tiền"))
                .andExpect(jsonPath("$.data.items[2].kind").value("PAYMENT"))
                .andExpect(jsonPath("$.data.items[2].direction").value("IN"))
                .andExpect(jsonPath("$.data.items[2].code").value("TXN-TX-1"))
                .andExpect(jsonPath("$.data.items[2].paymentTransactionId").exists())
                .andExpect(jsonPath("$.data.items[2].invoiceId").value(invoice))
                .andExpect(jsonPath("$.data.items[3].kind").value("INVOICE"))
                .andExpect(jsonPath("$.data.items[3].status").value("Paid"))
                .andExpect(jsonPath("$.data.items[3].method").value(nullValue()))
                .andExpect(jsonPath("$.data.items[3].invoiceRefundStatus").value("AwaitingConfirmation"))
                .andExpect(jsonPath("$.data.items[3].unitCode").value("A-01"));
    }

    @Test
    void historyFiltersByKindDirectionStatusAndTime() throws Exception {
        long order = insertOrder(q7, unitId("A-01"), customer1);
        long invoice = insertInvoice(order, customer1, "INV-TX-FILTER", "Đặt cọc", "Deposit",
                "Paid", "500000", "5 hours");
        insertPayment(invoice, "TXN-TX-SUCCESS", "Success", "500000", "5 hours", "5 hours");
        insertPayment(invoice, "TXN-TX-DUP", "Duplicate", "500000", "4 hours", null);
        insertPayment(invoice, "TXN-TX-PENDING", "Pending", "500000", "20 minutes", null);
        // Dữ liệu bẩn: Pending nhưng có paid_at; tuổi chờ phải tính từ created_at, không phải paid_at.
        insertPayment(invoice, "TXN-TX-PAIDPENDING", "Pending", "500000", "60 minutes", "5 minutes");
        long source = paymentId("TXN-TX-SUCCESS");
        insertRefund(invoice, source, "Success", "500000", "2 hours", "2 hours");

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("kind", "PAYMENT")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].code").value("TXN-TX-PENDING"))
                .andExpect(jsonPath("$.data.items[0].needsReconciliation").value(true))
                .andExpect(jsonPath("$.data.items[0].pendingAgeMinutes")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(15)))
                .andExpect(jsonPath("$.data.items[1].code").value("TXN-TX-PAIDPENDING"))
                .andExpect(jsonPath("$.data.items[1].needsReconciliation").value(true))
                .andExpect(jsonPath("$.data.items[2].code").value("TXN-TX-DUP"))
                .andExpect(jsonPath("$.data.items[2].needsReconciliation").value(false))
                .andExpect(jsonPath("$.data.items[3].code").value("TXN-TX-SUCCESS"));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("needsReconciliation", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].code").value("TXN-TX-PENDING"))
                .andExpect(jsonPath("$.data.items[1].code").value("TXN-TX-PAIDPENDING"));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("direction", "OUT")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("REFUND"));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("status", "Duplicate")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("TXN-TX-DUP"));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("refundStatus", "Refunded")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        jdbc.update("update invoices set refund_status = 'Required' where id = ?", invoice);
        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("refundStatus", "Required")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("INV-TX-FILTER"))
                .andExpect(jsonPath("$.data.items[0].invoiceRefundStatus").value("Required"));

        Instant from = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant to = Instant.now().minus(1, ChronoUnit.HOURS);
        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                // PAIDPENDING neo ở created_at (60 phút), nên rơi đúng vào khoảng 3h–1h.
                .andExpect(jsonPath("$.data.items[0].code").value("TXN-TX-PAIDPENDING"))
                .andExpect(jsonPath("$.data.items[1].kind").value("REFUND"));
    }

    @Test
    void historyPaginatesUnion() throws Exception {
        long order1 = insertOrder(q7, unitId("A-01"), customer1);
        insertInvoice(order1, customer1, "INV-TX-PAGE-1", "Đặt cọc 1", "Deposit", "Unpaid",
                "500000", "3 hours");
        long order2 = insertOrder(q7, unitId("A-02"), customer1);
        insertInvoice(order2, customer1, "INV-TX-PAGE-2", "Đặt cọc 2", "Deposit", "Unpaid",
                "500000", "2 hours");
        long order3 = insertOrder(q7, unitId("A-03"), customer1);
        insertInvoice(order3, customer1, "INV-TX-PAGE-3", "Đặt cọc 3", "Deposit", "Unpaid",
                "500000", "1 hour");

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].code").value("INV-TX-PAGE-3"))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("INV-TX-PAGE-1"));
    }

    @Test
    void historyOnlyReturnsFacilityTransactions() throws Exception {
        long tdUnit = insertUnit("TD-TX-1", td);
        long tdOrder = insertOrder(td, tdUnit, customer1);
        insertInvoice(tdOrder, customer1, "INV-TX-TD", "Đặt cọc TD", "Deposit", "Unpaid",
                "500000", "1 hour");
        long q7Order = insertOrder(q7, unitId("A-01"), customer1);
        insertInvoice(q7Order, customer1, "INV-TX-Q7", "Đặt cọc Q7", "Deposit", "Unpaid",
                "500000", "1 hour");

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("INV-TX-Q7"));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(td))
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("customer1@lemar.vn")))
                .andExpect(status().isForbidden());
    }

    @Test
    void historyRejectsInvalidFiltersAndUnknownFacility() throws Exception {
        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("kind", "Broken")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Loại giao dịch không hợp lệ: Broken"));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("direction", "SIDEWAYS")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chiều dòng tiền không hợp lệ: SIDEWAYS"));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("status", "Broken")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trạng thái giao dịch không hợp lệ: Broken"));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("from", "09-11-2026")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Thời điểm bắt đầu không hợp lệ (cần ISO-8601 UTC): 09-11-2026"));

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7))
                        .param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", "99999")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/fm/transactions")
                        .param("facilityId", String.valueOf(q7)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------- helpers

    private String bearerOf(String email) {
        Account account = accountRepository.findByEmail(email).orElseThrow();
        return "Bearer " + jwtService.issueAccessToken(account);
    }

    private long facilityId(String code) {
        return jdbc.queryForObject("select id from facilities where code = ?", Long.class, code);
    }

    private long accountId(String email) {
        return jdbc.queryForObject("select id from accounts where email = ?", Long.class, email);
    }

    private long unitId(String code) {
        return jdbc.queryForObject("select id from storage_units where code = ?", Long.class, code);
    }

    private long paymentId(String txnRef) {
        return jdbc.queryForObject("select id from payment_transactions where vnp_txn_ref = ?",
                Long.class, txnRef);
    }

    private long insertUnit(String code, long facility) {
        return jdbc.queryForObject("""
                insert into storage_units (code, facility_id, unit_type_id, status)
                values (?, ?, (select id from unit_types where name = 'Small'), 'Available')
                returning id
                """, Long.class, code, facility);
    }

    private long insertOrder(long facility, long unit, long customer) {
        Long requestId = jdbc.queryForObject("""
                insert into rental_requests (facility_id, customer_name, normalized_customer_email,
                    customer_phone, unit_type_id, start_date, period, status)
                values (?, 'Khách test', ?, '0901234567',
                    (select id from unit_types where name = 'Small'), current_date, 6, 'Converted')
                returning id
                """, Long.class, facility, "customer1@lemar.vn");
        return jdbc.queryForObject("""
                insert into rental_orders (request_id, customer_id, unit_id, status)
                values (?, ?, ?, 'Deposited') returning id
                """, Long.class, requestId, customer, unit);
    }

    private long insertInvoice(long orderId, long customerId, String code, String title, String type,
                               String status, String amount, String createdAtOffset) {
        return jdbc.queryForObject("""
                insert into invoices (order_id, customer_id, code, title, type, status, amount, created_at, due_date)
                values (?, ?, ?, ?, ?, ?, ?, now() - cast(? as interval), now() + interval '1 day')
                returning id
                """, Long.class, orderId, customerId, code, title, type, status,
                new BigDecimal(amount), createdAtOffset);
    }

    private long insertPayment(long invoiceId, String txnRef, String status, String amount,
                               String createdAtOffset, String paidAtOffset) {
        return jdbc.queryForObject("""
                insert into payment_transactions (invoice_id, vnp_txn_ref, amount, direction, status,
                    paid_at, created_at)
                values (?, ?, ?, 'PAY', ?, now() - cast(? as interval), now() - cast(? as interval))
                returning id
                """, Long.class, invoiceId, txnRef, new BigDecimal(amount), status,
                paidAtOffset, createdAtOffset);
    }

    private void insertRefund(long invoiceId, long sourceTransactionId, String status, String amount,
                              String createdAtOffset, String disbursedAtOffset) {
        jdbc.update("""
                insert into refund_transactions (invoice_id, source_transaction_id, amount, method,
                    status, reason_code, gateway_ref, disbursed_at, created_at, updated_at)
                values (?, ?, ?, 'CASH', ?, 'UNIT_UNAVAILABLE', ?, now() - cast(? as interval),
                    now() - cast(? as interval), now() - cast(? as interval))
                """, invoiceId, sourceTransactionId, new BigDecimal(amount), status,
                "GW-TX-REFUND", disbursedAtOffset, createdAtOffset, createdAtOffset);
    }
}
