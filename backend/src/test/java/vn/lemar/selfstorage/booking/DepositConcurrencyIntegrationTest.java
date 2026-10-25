package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.booking.application.DepositPaymentService;
import vn.lemar.selfstorage.booking.application.exception.DepositNotAllowedException;

/**
 * Hai khách bấm thanh toán cùng lúc trên cùng một khoang — tiêu chí "Xong khi" của #114.
 *
 * <p>Khóa phiên là phép đọc rồi mới ghi, nên nếu không có gì chặn ở giữa thì cả hai luồng cùng
 * thấy "khoang rảnh" và cùng mở phiên. Test này phải đỏ nếu hàng rào đó biến mất.
 *
 * <p>Không {@code @Transactional}: hai luồng phải nhìn thấy dữ liệu commit của nhau.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DepositConcurrencyIntegrationTest {

    private static final String CUSTOMER_EMAIL = "customer1@lemar.vn";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DepositPaymentService service;

    @Autowired
    private JdbcTemplate jdbc;

    private Long invoiceId;
    private Long rivalInvoiceId;
    private String rivalEmail;
    private Long unitId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM order_notifications");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM payment_transactions");
        jdbc.update("DELETE FROM invoices");
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
                        + " VALUES (?, 'Nguyen Van A', ?, '0900000000', ?, CURRENT_DATE, 1, 'Approved')"
                        + " RETURNING id",
                Long.class, facilityId, CUSTOMER_EMAIL, unitTypeId);
        Long orderId = jdbc.queryForObject(
                "INSERT INTO rental_orders (request_id, customer_id, unit_id, status, expires_at)"
                        + " VALUES (?, ?, ?, 'Pending', now() + interval '3 days') RETURNING id",
                Long.class, requestId, customerId, unitId);
        invoiceId = seedInvoice(orderId, customerId, "INV-DEP-RACE-A");

        // Khách thứ hai, đơn thứ hai, nhưng cùng khoang — đây mới là ca nhánh 4 phải chặn.
        Long rivalId = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE email <> ? AND email_verified_at IS NOT NULL"
                        + " ORDER BY id LIMIT 1", Long.class, CUSTOMER_EMAIL);
        rivalEmail = jdbc.queryForObject("SELECT email FROM accounts WHERE id = ?", String.class, rivalId);
        Long rivalRequestId = jdbc.queryForObject(
                "INSERT INTO rental_requests (facility_id, customer_name, normalized_customer_email,"
                        + " customer_phone, unit_type_id, start_date, period, status)"
                        + " VALUES (?, 'Tran Thi B', 'rival@test.local', '0900000001', ?, CURRENT_DATE,"
                        + " 1, 'Approved') RETURNING id",
                Long.class, facilityId, unitTypeId);
        Long rivalOrderId = jdbc.queryForObject(
                "INSERT INTO rental_orders (request_id, customer_id, unit_id, status, expires_at)"
                        + " VALUES (?, ?, ?, 'Pending', now() + interval '3 days') RETURNING id",
                Long.class, rivalRequestId, rivalId, unitId);
        rivalInvoiceId = seedInvoice(rivalOrderId, rivalId, "INV-DEP-RACE-B");
    }

    private Long seedInvoice(Long orderId, Long customerId, String code) {
        return jdbc.queryForObject(
                "INSERT INTO invoices (order_id, customer_id, code, title, type, status, amount, due_date)"
                        + " VALUES (?, ?, ?, 'Dat coc khoang chua', 'Deposit', 'Unpaid',"
                        + " 10000.00, now() + interval '3 days') RETURNING id",
                Long.class, orderId, customerId, code);
    }

    @Test
    void twoCustomersRacingForTheSameUnitOpenExactlyOneSession() throws Exception {
        List<Outcome> outcomes = callInParallel();

        assertThat(outcomes).filteredOn(Outcome::opened)
                .as("đúng một luồng được mở phiên: %s", outcomes)
                .hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.opened())
                .allSatisfy(blocked -> assertThat(blocked.message())
                        .contains("đang trong quá trình xử lý thanh toán"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payment_transactions WHERE status = 'Pending'", Integer.class))
                .as("chỉ được một giao dịch Pending cho một khoang").isEqualTo(1);
    }

    private List<Outcome> callInParallel() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLine = new CountDownLatch(1);
        try {
            List<Callable<Outcome>> calls = new ArrayList<>();
            for (Map.Entry<Long, String> target
                    : Map.of(invoiceId, CUSTOMER_EMAIL, rivalInvoiceId, rivalEmail).entrySet()) {
                calls.add(() -> {
                    // SecurityContext là ThreadLocal; mỗi luồng đăng nhập bằng chủ hóa đơn của nó.
                    authenticateCurrentThread(target.getValue());
                    startLine.await();
                    try {
                        service.startDeposit(target.getKey(), "1.2.3.4");
                        return new Outcome(true, null);
                    } catch (DepositNotAllowedException e) {
                        return new Outcome(false, e.getMessage());
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                });
            }
            List<Future<Outcome>> futures = calls.stream().map(pool::submit).toList();

            startLine.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    /** SecurityContext là ThreadLocal nên mỗi luồng phải tự đặt, không kế thừa từ luồng test. */
    private void authenticateCurrentThread(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        List.of(new SimpleGrantedAuthority("ACCOUNT_ACTIVE"))));
    }

    private record Outcome(boolean opened, String message) {
    }
}
