package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.identity.application.JwtService;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("refund-obligation")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class RefundObligationIntegrationTest {

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
    private AccountRepository accounts;

    private Long orderId;
    private Long invoiceId;

    @BeforeEach
    void seed() {
        Long requestId = jdbc.queryForObject("""
                insert into rental_requests (facility_id, customer_name, normalized_customer_email,
                    customer_phone, unit_type_id, start_date, period, status)
                values (1, 'Khách refund obligation', 'customer1@example.com', '0901234567', 1,
                    current_date, 6, 'Converted')
                returning id
                """, Long.class);
        orderId = jdbc.queryForObject("""
                insert into rental_orders (request_id, customer_id, unit_id, status)
                values (?, (select id from accounts where email = 'customer1@example.com'), 1, 'Deposited')
                returning id
                """, Long.class, requestId);
        invoiceId = jdbc.queryForObject("""
                insert into invoices (order_id, customer_id, code, title, type, status, amount,
                    created_at, due_date)
                values (?, (select id from accounts where email = 'customer1@example.com'),
                    'INV-REFUND-REQUEST-TEST', 'Đặt cọc', 'Deposit', 'Paid', 1000000,
                    now() - interval '1 hour', now() + interval '2 days')
                returning id
                """, Long.class, orderId);
        jdbc.update("""
                insert into payment_transactions (invoice_id, vnp_txn_ref, amount, direction, status,
                    gateway_transaction_no, paid_at, created_at)
                values (?, 'TXN-OBLIGATION-TEST', 1000000, 'PAY', 'Success', 'GW-OBLIGATION-TEST',
                    now(), now())
                """, invoiceId);
    }

    @Test
    void fmCreatesRequestWithServerCalculatedAmountAndLines() throws Exception {
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invoiceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceId").value(invoiceId))
                .andExpect(jsonPath("$.data.amount").value(800000))
                .andExpect(jsonPath("$.data.status").value("Open"))
                .andExpect(jsonPath("$.data.formulaText").value("1000000 - 200000 = 800000"))
                .andExpect(jsonPath("$.data.lines.length()").value(2));

        assertThat(jdbc.queryForObject(
                "select amount from refund_obligations where invoice_id = ?", java.math.BigDecimal.class,
                invoiceId)).isEqualByComparingTo("800000");
        assertThat(jdbc.queryForObject(
                "select count(*) from refund_obligation_lines where refund_obligation_id = "
                + "(select id from refund_obligations where invoice_id = ?)", Integer.class, invoiceId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'REFUND_OBLIGATION_CREATED' "
                        + "and entity_type = 'RefundObligation'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void orderOnlyRequestIsAllowed() throws Exception {
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceId").doesNotExist())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.amount").value(800000));
    }

    @Test
    void twoOrderOnlyRequestsForSameOrderAreAllowed() throws Exception {
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select count(*) from refund_obligations where order_id = ? and invoice_id is null",
                Integer.class, orderId)).isEqualTo(2);
    }

    @Test
    void invoiceBoundAndOrderOnlyRequestForSameOrderAreAllowed() throws Exception {
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invoiceId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select count(*) from refund_obligations where order_id = ?", Integer.class, orderId))
                .isEqualTo(2);
    }

    @Test
    void orderOnlyRequestWithUnknownOrderReturnsNotFound() throws Exception {
        String json = body(null).replace("\"orderId\": " + orderId, "\"orderId\": 999999999");
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherFacilityFmIsForbidden() throws Exception {
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm2@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invoiceId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCreateRefundObligation() throws Exception {
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("customer1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invoiceId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void zeroFinalAmountIsRejected() throws Exception {
        String json = body(invoiceId).replace("\"amount\":200000", "\"amount\":1000000");
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithoutInvoiceOrOrderIsRejected() throws Exception {
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null).replace("\"orderId\": " + orderId, "\"orderId\": null")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherReasonRequiresNote() throws Exception {
        String json = body(invoiceId).replace("\"reasonNote\": \"Điều chỉnh đặc biệt\"", "\"reasonNote\": \"\"");
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invoiceBoundObligationRequiresPaidInvoice() throws Exception {
        jdbc.update("update invoices set status = 'Unpaid' where id = ?", invoiceId);

        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invoiceId)))
                .andExpect(status().isConflict());
    }

    @Test
    void amountAbovePaidAmountIsRejected() throws Exception {
        String json = body(invoiceId).replace("\"amount\":1000000", "\"amount\":1500000");

        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handoverRejectedWhenObligationIsActive() throws Exception {
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invoiceId)))
                .andExpect(status().isOk());
        jdbc.update("update invoices set refund_status = 'Required', refund_reason = 'UNIT_UNAVAILABLE' "
                + "where id = ?", invoiceId);

        mockMvc.perform(post("/api/refunds/invoices/{id}/handover", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fs1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CASH","evidenceUrl":"https://example.com/receipt.jpg"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidReasonCodeIsRejected() throws Exception {
        String json = body(invoiceId).replace("\"reasonCode\": \"OTHER\"", "\"reasonCode\": \"BOGUS\"");

        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateActiveRequestIsRejected() throws Exception {
        String json = body(invoiceId);
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/refunds/obligations")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }

    private String body(Long invoice) {
        String invoicePart = invoice == null ? "null" : invoice.toString();
        return """
                {
                  "invoiceId": %s,
                  "orderId": %d,
                  "reasonCode": "OTHER",
                  "reasonNote": "Điều chỉnh đặc biệt",
                  "calculationVersion": "TEST_V1",
                  "calculationInput": {"paidDeposit": 1000000, "unpaidRent": 200000},
                  "lines": [
                    {"componentCode":"DEPOSIT_PAID","direction":"ADD","amount":1000000,
                     "label":"Cọc đã thu"},
                    {"componentCode":"UNPAID_RENT","direction":"SUBTRACT","amount":200000,
                     "label":"Tiền thuê còn thiếu"}
                  ]
                }
                """.formatted(invoicePart, orderId);
    }

    private String bearerOf(String email) {
        Account account = accounts.findByEmail(email).orElseThrow();
        return "Bearer " + jwtService.issueAccessToken(account);
    }
}
