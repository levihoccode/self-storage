package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.dto.IpnResponse;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;

/**
 * VNPay retry tới 10 lần, và không có gì bảo đảm hai lần retry không chạm server cùng lúc.
 *
 * <p>Test này bắn hai IPN song song cùng {@code vnp_TxnRef} trên Postgres thật. Guard
 * {@code isPending()} đơn thuần không chặn được: hai transaction cùng đọc {@code Pending} rồi
 * cùng ghi {@code Success}, và cùng bắn {@code PaymentSucceeded} — tức ghi nhận trùng tiền.
 * Unique constraint {@code vnp_txn_ref} không cứu được vì cả hai update <em>cùng một row</em>.
 *
 * <p>Chữ ký không phải thứ đang kiểm ở đây nên {@code PaymentGateway} được mock —
 * {@link VnPayGatewayTest} lo phần đó.
 */
// Secret chung cho test nằm ở `src/test/resources/application.properties`; ở đây chỉ ghim
// `vnpay.tmn-code` vì test đối chiếu trực tiếp giá trị này trong payload IPN.
@SpringBootTest(properties = "vnpay.tmn-code=" + PaymentIpnConcurrencyTest.TMN_CODE)
@Testcontainers(disabledWithoutDocker = true)
class PaymentIpnConcurrencyTest {

    static final String TMN_CODE = "TESTTMN1";

    private static final String TXN_REF = "TXNRACE001";
    private static final BigDecimal AMOUNT = new BigDecimal("10000.00");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private PaymentGateway gateway;

    @Autowired
    private PaymentService service;

    @Autowired
    private PaymentTransactionRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void haiIpnSongSongChiDuocGhiNhanMotLan() throws Exception {
        when(gateway.verifySignature(anyMap())).thenReturn(true);

        long invoiceId = seedInvoice();
        repository.saveAndFlush(new PaymentTransaction(invoiceId, TXN_REF, AMOUNT, "coc thue kho"));

        List<IpnResponse> responses = callIpnConcurrently(2);

        // Đúng một lần được ghi nhận; lần còn lại phải bị chặn bằng 02.
        assertThat(responses).extracting(IpnResponse::rspCode)
                .as("hai IPN song song: %s", responses)
                .containsExactlyInAnyOrder("00", "02");

        // Đọc bằng JDBC chứ không qua repository: findByVnpTxnRef nay khoá pessimistic nên
        // đòi phải nằm trong transaction.
        assertThat(statusInDb()).isEqualTo(PaymentStatus.SUCCESS.dbValue());
        assertThat(countSuccessRows()).isEqualTo(1);
    }

    private List<IpnResponse> callIpnConcurrently(int threads) throws Exception {
        Map<String, String> ipn = Map.of(
                "vnp_TxnRef", TXN_REF,
                "vnp_TmnCode", TMN_CODE,
                "vnp_Amount", "1000000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "14123456",
                "vnp_PayDate", "20260930163700",
                "vnp_SecureHash", "khong-dung-toi-vi-gateway-da-mock");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLine = new CountDownLatch(1);
        try {
            List<Callable<IpnResponse>> calls = java.util.Collections.nCopies(threads, () -> {
                startLine.await();
                return service.handleIpn(ipn);
            });
            List<Future<IpnResponse>> futures = calls.stream().map(pool::submit).toList();

            startLine.countDown(); // thả cả hai cùng lúc
            List<IpnResponse> responses = new java.util.ArrayList<>();
            for (Future<IpnResponse> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private String statusInDb() {
        return jdbc.queryForObject(
                "SELECT status FROM payment_transactions WHERE vnp_txn_ref = ?", String.class, TXN_REF);
    }

    private int countSuccessRows() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM payment_transactions WHERE vnp_txn_ref = ? AND status = 'Success'",
                Integer.class, TXN_REF);
        return count == null ? 0 : count;
    }

    /** `payment_transactions.invoice_id` là FK NOT NULL nên phải dựng chuỗi request → order → invoice. */
    private long seedInvoice() {
        Long customerId = jdbc.queryForObject(
                "SELECT id FROM accounts ORDER BY id LIMIT 1", Long.class);
        Long facilityId = jdbc.queryForObject(
                "SELECT id FROM facilities ORDER BY id LIMIT 1", Long.class);
        Long unitTypeId = jdbc.queryForObject(
                "SELECT id FROM unit_types ORDER BY id LIMIT 1", Long.class);

        Long requestId = jdbc.queryForObject(
                "INSERT INTO rental_requests (facility_id, customer_name, normalized_customer_email,"
                        + " customer_phone, unit_type_id, start_date, period, status)"
                        + " VALUES (?, 'Nguyen Van A', 'race@test.local', '0900000000', ?, CURRENT_DATE, 1, 'Pending')"
                        + " RETURNING id",
                Long.class, facilityId, unitTypeId);

        Long orderId = jdbc.queryForObject(
                "INSERT INTO rental_orders (request_id, customer_id, status) VALUES (?, ?, 'Pending')"
                        + " RETURNING id",
                Long.class, requestId, customerId);

        Long invoiceId = jdbc.queryForObject(
                "INSERT INTO invoices (order_id, customer_id, code, title, type, status, amount)"
                        + " VALUES (?, ?, 'INV-RACE-0001', 'Coc thue kho', 'Deposit', 'Unpaid', ?)"
                        + " RETURNING id",
                Long.class, orderId, customerId, AMOUNT);

        return invoiceId;
    }
}
