package vn.lemar.selfstorage.overdue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.e2e.HttpE2ESupport;
import vn.lemar.selfstorage.payment.application.PaymentGateway;

/**
 * Flow 6 end-to-end: hợp đồng đã quá hạn → cron cộng phí quá hạn → quá hạn kéo dài tạm khoá truy cập → khách gia hạn
 * và trả phí → hệ thống mở lại truy cập.
 *
 * <p>Điểm vào của Flow 6 là hợp đồng {@code Active}; chặng 1 → {@code Active} (bàn giao, ký, thu tháng đầu) là Flow
 * 1.5 + Flow 2 nên fixture dựng thẳng bằng SQL, không dựng lại ở đây. Mọi thao tác còn lại đi qua HTTP thật và
 * {@link OverdueJob} — đúng đường cron chạy ở production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class Flow6HttpE2ETest extends HttpE2ESupport {

    private static final String CUSTOMER = "customer1@lemar.vn";
    private static final String FM = "fm1@lemar.vn";
    private static final BigDecimal MONTHLY = new BigDecimal("100000");
    private static final BigDecimal FEE_PER_DAY = new BigDecimal("20000");
    private static final long GRACE_DAYS = 3;
    private static final long ESCALATION_DAYS = 30;
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private PaymentGateway gateway;
    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private OverdueJob overdueJob;

    private long facilityId;
    private long customerId;

    @BeforeEach
    void setUp() {
        facilityId = id("select id from facilities where code = 'Q7'");
        customerId = id("select id from accounts where email = ?", CUSTOMER);
        when(gateway.buildPaymentUrl(any())).thenReturn("https://pay.test/contract");
        when(gateway.verifySignature(anyMap())).thenReturn(true);
        when(gateway.newTxnRef(any())).thenCallRealMethod();
        when(mailSender.createMimeMessage()).thenReturn(
                new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
    }

    @Test
    void chargesSuspendsAndRestoresAccessAfterPayment() {
        LocalDate today = LocalDate.now(VN);
        long endDate = 10;
        Seed seed = seedActive(today.minusDays(endDate));

        // ---------------------------------------------------------------- cron lượt 1: cộng phí
        overdueJob.run();

        long overdueFeeId = id("select id from invoices where contract_id = ? and code like 'INV-OVD-%'",
                seed.contractId);
        BigDecimal accrued = accruedFee(seed.contractId);
        long chargedDays = endDate - GRACE_DAYS;
        assertThat(accrued).isEqualByComparingTo(FEE_PER_DAY.multiply(BigDecimal.valueOf(chargedDays)));
        assertThat(text("select status from invoices where id = ?", overdueFeeId)).isEqualTo("Unpaid");
        assertThat(notifications(CUSTOMER, "CONTRACT_OVERDUE", seed.orderId)).isEqualTo(1);
        assertThat(notifications(FM, "CONTRACT_OVERDUE", seed.orderId)).isEqualTo(1);

        // ---------------------------------------------------------------- FM xem qua HTTP
        JsonNode rows = ok(get("/api/fm/overdue-contracts?facilityId=" + facilityId, FM));
        JsonNode row = find(rows, seed.contractId);
        assertThat(row).as("hợp đồng quá hạn phải có trong danh sách FM").isNotNull();
        assertThat(row.path("daysOverdue").asLong()).isEqualTo(endDate);
        assertThat(row.path("chargedDays").asLong()).isEqualTo(chargedDays);
        assertThat(row.path("accrued").decimalValue()).isEqualByComparingTo(accrued);
        assertThat(row.path("openInvoice").path("code").asText()).startsWith("INV-OVD-");
        assertThat(row.path("returnRequested").asBoolean()).isFalse();

        // ---------------------------------------------------------------- khách thấy hợp đồng quá hạn
        JsonNode rental = find(ok(get("/api/customer/rentals", CUSTOMER)), seed.contractId);
        assertThat(rental).isNotNull();
        assertThat(rental.path("overdue").asBoolean()).isTrue();
        assertThat(rental.path("daysLeft").asLong()).isEqualTo(-endDate);

        // ---------------------------------------------------------------- quá hạn kéo dài → tạm khoá
        jdbc.update("update rental_contracts set end_date = ? where id = ?", today.minusDays(ESCALATION_DAYS + 10),
                seed.contractId);
        overdueJob.run();

        assertThat(count("select count(*) from rental_contracts where id = ? and access_suspended_at is not null",
                seed.contractId)).isEqualTo(1);
        assertThat(text("select status from unit_access_keys where contract_id = ? and access_type = 'AccessCode'",
                seed.contractId)).isEqualTo("Revoked");
        assertThat(auditCount("CONTRACT_ACCESS_SUSPENDED", "RentalContract", seed.contractId)).isEqualTo(1);
        assertThat(notifications(CUSTOMER, "CONTRACT_ACCESS_SUSPENDED", seed.orderId)).isEqualTo(1);
        assertThat(notifications(FM, "CONTRACT_ACCESS_SUSPENDED", seed.orderId)).isEqualTo(1);
        BigDecimal escalated = FEE_PER_DAY.multiply(BigDecimal.valueOf(ESCALATION_DAYS + 10 - GRACE_DAYS));
        assertThat(accruedFee(seed.contractId)).isEqualByComparingTo(escalated);

        // Đang tạm khoá → không cấp được mã mới.
        assertThat(post("/api/customer/contracts/" + seed.contractId + "/access-code", CUSTOMER, null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // ---------------------------------------------------------------- gia hạn phủ kỳ quá hạn
        JsonNode extension = ok(post("/api/customer/rentals/" + seed.contractId + "/extensions", CUSTOMER,
                Map.of("months", 2)));
        long extensionInvoiceId = extension.path("invoiceId").asLong();
        assertThat(extension.path("code").asText()).startsWith("INV-EXT-Q7-");
        assertThat(extension.path("amount").decimalValue()).isEqualByComparingTo(MONTHLY.multiply(
                BigDecimal.valueOf(2)));

        ok(post("/api/invoices/" + extensionInvoiceId + "/pay", CUSTOMER, null));
        assertThat(ipn(latestTxnRef(extensionInvoiceId), extension.path("amount").decimalValue())).isEqualTo("00");
        assertThat(text("select status from invoices where id = ?", extensionInvoiceId)).isEqualTo("Paid");
        assertThat(id("select period from rental_contracts where id = ?", seed.contractId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select end_date from rental_contracts where id = ?", LocalDate.class,
                seed.contractId)).isAfter(today);
        assertThat(auditCount("CONTRACT_EXTENDED", "RentalContract", seed.contractId)).isEqualTo(1);

        // Gia hạn dừng cộng thêm phí nhưng KHÔNG xoá khoản đã phát sinh: vẫn đúng một hoá đơn INV-OVD, giữ nguyên số.
        overdueJob.run();
        assertThat(count("select count(*) from invoices where contract_id = ? and code like 'INV-OVD-%'",
                seed.contractId)).isEqualTo(1);
        assertThat(text("select status from invoices where id = ?", overdueFeeId)).isEqualTo("Unpaid");
        assertThat(accruedFee(seed.contractId)).isEqualByComparingTo(escalated);

        // ---------------------------------------------------------------- trả phí → mở lại truy cập
        ok(post("/api/invoices/" + overdueFeeId + "/pay", CUSTOMER, null));
        assertThat(ipn(latestTxnRef(overdueFeeId), escalated)).isEqualTo("00");
        assertThat(text("select status from invoices where id = ?", overdueFeeId)).isEqualTo("Paid");

        overdueJob.run();
        assertThat(count("select count(*) from rental_contracts where id = ? and access_suspended_at is null",
                seed.contractId)).isEqualTo(1);
        assertThat(auditCount("CONTRACT_ACCESS_RESTORED", "RentalContract", seed.contractId)).isEqualTo(1);
        assertThat(notifications(CUSTOMER, "CONTRACT_ACCESS_RESTORED", seed.orderId)).isEqualTo(1);

        JsonNode newCode = ok(post("/api/customer/contracts/" + seed.contractId + "/access-code", CUSTOMER, null));
        assertThat(newCode.path("accessCode").asText()).isNotBlank();
        assertThat(count("select count(*) from unit_access_keys where contract_id = ? and access_type = 'AccessCode'"
                + " and status = 'Active'", seed.contractId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ fixture

    private record Seed(long unitId, long orderId, long contractId) {
    }

    /** Hợp đồng {@code Active} đã bàn giao: khoang {@code Rented}, đơn {@code Done}, hai loại khoá đã cấp. */
    private Seed seedActive(LocalDate endDate) {
        String suffix = SEQ.incrementAndGet() + "-" + System.nanoTime() % 100000;
        LocalDate startDate = endDate.minusMonths(3).plusDays(1);
        long unitId = id("insert into storage_units (code, facility_id, unit_type_id, status) values ('E6-" + suffix
                + "', " + facilityId + ", (select id from unit_types where name = 'Small'), 'Rented') returning id");
        long requestId = id("insert into rental_requests (facility_id, customer_name, normalized_customer_email,"
                + " customer_phone, unit_type_id, start_date, period, status, unit_id) values (" + facilityId
                + ", 'Khách Flow 6', '" + CUSTOMER + "', '0900000000', (select id from unit_types where name ="
                + " 'Small'), '" + startDate + "', 3, 'Converted', " + unitId + ") returning id");
        long orderId = id("insert into rental_orders (request_id, customer_id, unit_id, status, expires_at) values ("
                + requestId + ", " + customerId + ", " + unitId + ", 'Done', now() + interval '20 days') returning id");
        long contractId = id("insert into rental_contracts (order_id, customer_id, unit_id, code, terms_version,"
                + " monthly_price, deposit_amount, period, start_date, end_date, signed_at, status) values (" + orderId
                + ", " + customerId + ", " + unitId + ", 'HD-E6-" + suffix + "', 'builtin-v1', " + MONTHLY + ", "
                + MONTHLY + ", 3, '" + startDate + "', '" + endDate + "', now(), 'Active') returning id");
        jdbc.update("insert into unit_access_keys (unit_id, contract_id, access_type, quantity)"
                + " values (?, ?, 'PhysicalKey', 1)", unitId, contractId);
        jdbc.update("insert into unit_access_keys (unit_id, contract_id, access_type, code_hash)"
                + " values (?, ?, 'AccessCode', 'hash')", unitId, contractId);
        return new Seed(unitId, orderId, contractId);
    }

    /** Số tiền hoá đơn phí quá hạn còn mở của hợp đồng. */
    private BigDecimal accruedFee(long contractId) {
        return jdbc.queryForObject("select amount from invoices where contract_id = ? and code like 'INV-OVD-%'"
                + " and status = 'Unpaid'", BigDecimal.class, contractId);
    }

    private static JsonNode find(JsonNode array, long contractId) {
        for (JsonNode node : array) {
            if (node.path("contractId").asLong() == contractId) {
                return node;
            }
        }
        return null;
    }
}
