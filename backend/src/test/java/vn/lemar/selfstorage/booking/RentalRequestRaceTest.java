package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.domain.RentalRequestRejectReason;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;

/**
 * Race test trên Postgres thật — spec draft.md:135 yêu cầu thao tác duyệt chạy conditional
 * update {@code WHERE status = 'Pending'}: 2 transaction song song, chỉ 1 tx cập nhật được row,
 * tx sau nhận 0 row (service map thành 409). Test trực tiếp cơ chế ở tầng repository; phần map
 * 0 row → 409 nằm ở unit test service. Máy không có Docker thì test tự skip — CI chạy.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class RentalRequestRaceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RentalRequestRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentApproveOnlyOneWins() throws Exception {
        Long id = insertPendingRequest();
        Long unitA = unitId("A-01");
        Long unitB = unitId("A-02");

        List<Integer> results = runConcurrently(
                () -> inNewTx(() -> repository.approveIfPending(id, unitA, Instant.now(),
                        expiry(), RentalRequestStatus.Pending, RentalRequestStatus.Approved)),
                () -> inNewTx(() -> repository.approveIfPending(id, unitB, Instant.now(),
                        expiry(), RentalRequestStatus.Pending, RentalRequestStatus.Approved)));

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(statusOf(id)).isEqualTo("Approved");
    }

    @Test
    void concurrentRejectOnlyOneWins() throws Exception {
        Long id = insertPendingRequest();

        List<Integer> results = runConcurrently(
                () -> inNewTx(() -> repository.rejectIfPending(id,
                        RentalRequestRejectReason.UNIT_UNAVAILABLE, null, Instant.now(),
                        RentalRequestStatus.Pending, RentalRequestStatus.Rejected)),
                () -> inNewTx(() -> repository.rejectIfPending(id,
                        RentalRequestRejectReason.CUSTOMER_REQUEST, "note", Instant.now(),
                        RentalRequestStatus.Pending, RentalRequestStatus.Rejected)));

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(statusOf(id)).isEqualTo("Rejected");
    }

    @Test
    void concurrentApproveAndRejectOnlyOneWins() throws Exception {
        Long id = insertPendingRequest();
        Long unitA = unitId("A-01");

        List<Integer> results = runConcurrently(
                () -> inNewTx(() -> repository.approveIfPending(id, unitA, Instant.now(),
                        expiry(), RentalRequestStatus.Pending, RentalRequestStatus.Approved)),
                () -> inNewTx(() -> repository.rejectIfPending(id,
                        RentalRequestRejectReason.CUSTOMER_REQUEST, null, Instant.now(),
                        RentalRequestStatus.Pending, RentalRequestStatus.Rejected)));

        assertThat(results).containsExactlyInAnyOrder(1, 0);
    }

    private Long insertPendingRequest() {
        RentalRequest saved = repository.save(new RentalRequest(
                facilityId("Q7"), "Race Test", "race@example.com", "0901234567",
                unitTypeId("Small"), LocalDate.now().plusDays(1), 6, expiry()));
        return saved.getId();
    }

    private Instant expiry() {
        return Instant.now().plusSeconds(7L * 24L * 3600L);
    }

    private int inNewTx(Callable<Integer> update) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return update.call();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private List<Integer> runConcurrently(Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> firstResult = pool.submit(gated(first, start));
            Future<Integer> secondResult = pool.submit(gated(second, start));
            start.countDown();
            List<Integer> results = new ArrayList<>();
            results.add(firstResult.get(15, TimeUnit.SECONDS));
            results.add(secondResult.get(15, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> gated(Callable<Integer> task, CountDownLatch start) {
        return () -> {
            start.await();
            return task.call();
        };
    }

    private Long facilityId(String code) {
        return jdbc.queryForObject("select id from facilities where code = ?", Long.class, code);
    }

    private Long unitTypeId(String name) {
        return jdbc.queryForObject("select id from unit_types where name = ?", Long.class, name);
    }

    private Long unitId(String code) {
        return jdbc.queryForObject("select id from storage_units where code = ?", Long.class, code);
    }

    private String statusOf(Long id) {
        return jdbc.queryForObject("select status from rental_requests where id = ?",
                String.class, id);
    }
}
