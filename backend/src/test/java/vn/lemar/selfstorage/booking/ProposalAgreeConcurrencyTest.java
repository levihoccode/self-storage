package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.booking.application.AgreeResult;
import vn.lemar.selfstorage.booking.application.ProposalService;
import vn.lemar.selfstorage.booking.application.exception.ProposalConflictException;

/**
 * Chạy thật trên Postgres: hai request {@code agree} đồng thời trên hai proposal {@code Pending}
 * của cùng một đơn. Đơn chỉ được tạo một hoá đơn cọc; request còn lại nhận 409.
 *
 * <p>Không dùng {@code @Transactional} ở test: mỗi request phải commit độc lập để tái hiện race.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ProposalAgreeConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProposalService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoConcurrentAgreesCreateOnlyOneDepositInvoice() throws Exception {
        long customerId = jdbc.queryForObject(
                "select id from accounts where email = 'customer1@lemar.vn'", Long.class);
        long facilityId = jdbc.queryForObject(
                "select id from facilities where code = 'Q7'", Long.class);
        long smallType = jdbc.queryForObject(
                "select id from unit_types where name = 'Small'", Long.class);
        long unitA = jdbc.queryForObject(
                "select id from storage_units where code = 'A-01'", Long.class);
        long unitB = jdbc.queryForObject(
                "select id from storage_units where code = 'A-02'", Long.class);

        Long requestId = jdbc.queryForObject("""
                insert into rental_requests (facility_id, customer_name, normalized_customer_email,
                    customer_phone, unit_type_id, start_date, period, unit_id, status)
                values (?, 'Concurrent', 'customer1@lemar.vn', '0900000000', ?, current_date + 1, 3, ?, 'Converted')
                returning id""", Long.class, facilityId, smallType, unitA);
        long orderId = jdbc.queryForObject("""
                insert into rental_orders (request_id, customer_id, status, expires_at)
                values (?, ?, 'Pending', now() + interval '30 days') returning id""",
                Long.class, requestId, customerId);
        long first = jdbc.queryForObject("""
                insert into proposal_feedbacks (order_id, customer_id, unit_id, status)
                values (?, ?, ?, 'Pending') returning id""", Long.class, orderId, customerId, unitA);
        long second = jdbc.queryForObject("""
                insert into proposal_feedbacks (order_id, customer_id, unit_id, status)
                values (?, ?, ?, 'Pending') returning id""", Long.class, orderId, customerId, unitB);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Object> firstCall = () -> agreeWhenReleased(start, ready, first, customerId);
            Callable<Object> secondCall = () -> agreeWhenReleased(start, ready, second, customerId);
            Future<Object> firstResult = pool.submit(firstCall);
            Future<Object> secondResult = pool.submit(secondCall);
            assertThat(ready.await(5, TimeUnit.SECONDS)).as("cả hai thread sẵn sàng").isTrue();
            start.countDown();

            List<Object> results = List.of(
                    firstResult.get(15, TimeUnit.SECONDS),
                    secondResult.get(15, TimeUnit.SECONDS));

            assertThat(results).filteredOn(AgreeResult.class::isInstance).as("một request thành công")
                    .hasSize(1);
            assertThat(results).filteredOn(ProposalConflictException.class::isInstance)
                    .as("một request nhận 409").hasSize(1);
            assertThat(jdbc.queryForObject("""
                    select count(*) from invoices
                    where order_id = ? and type = 'Deposit' and status in ('Unpaid', 'Paid')""",
                    Long.class, orderId)).as("chỉ một hoá đơn cọc active").isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from proposal_feedbacks where order_id = ? and status = 'Pending'",
                    Long.class, orderId)).as("proposal thua vẫn Pending").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private Object agreeWhenReleased(CountDownLatch start, CountDownLatch ready, long proposalId,
                                     long customerId) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            return service.agree(proposalId, customerId);
        } catch (ProposalConflictException ex) {
            return ex;
        }
    }
}
