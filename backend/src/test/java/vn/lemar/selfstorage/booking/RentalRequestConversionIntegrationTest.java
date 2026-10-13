package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.booking.application.ConversionResult;
import vn.lemar.selfstorage.booking.application.ConversionResult.Outcome;
import vn.lemar.selfstorage.booking.application.RentalRequestConversionService;
import vn.lemar.selfstorage.booking.application.RentalRequestExpiryService;
import vn.lemar.selfstorage.scheduler.application.RentalRequestExpiryJob;

/**
 * B2 chạy thật trên Postgres (Testcontainers) với seed V2: convert khi FM duyệt (trigger 1), claim
 * sau xác minh email (trigger 2), request hết hạn, trùng khoang, claim đồng thời và email lỗi.
 *
 * <p>Không dùng {@code @Transactional} ở test: email gửi sau commit và các ca đồng thời cần
 * transaction thật của từng thread — dữ liệu được dọn ở {@link #cleanUp()}. Request do B1 tạo nên
 * test tự chèn bằng SQL. Máy không có Docker thì skip.
 */
@SpringBootTest(properties = "app.frontend-url=http://fe.test/")
@Testcontainers(disabledWithoutDocker = true)
class RentalRequestConversionIntegrationTest {

    private static final String VERIFIED = "customer1@lemar.vn";
    private static final String UNVERIFIED = "unverified@lemar.vn";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private RentalRequestConversionService service;
    @Autowired
    private RentalRequestExpiryService expiryService;
    @Autowired
    private RentalRequestExpiryJob expiryJob;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        // Seed V2 chưa có key spec `order.expiry_days` (chỉ có `order.deposit_expiry_days`) — bug #130.
        jdbc.update("insert into policies (key, value) values ('order.expiry_days', '30') "
                + "on conflict (key) do nothing");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from order_notifications");
        jdbc.update("delete from notifications");
        jdbc.update("delete from audit_logs");
        jdbc.update("delete from proposal_feedbacks");
        jdbc.update("delete from rental_orders");
        jdbc.update("delete from rental_requests");
        jdbc.update("update accounts set email_verified_at = null where email = ?", UNVERIFIED);
    }

    // ------------------------------------------------------------ trigger 1: FM duyệt

    @Test
    void approvalForVerifiedCustomerCreatesPendingOrderAndProposal() {
        long requestId = approvedRequest(VERIFIED, "A-01", 7);

        ConversionResult result = service.convertOnApproval(requestId);

        assertThat(result.outcome()).isEqualTo(Outcome.CONVERTED);
        long customerId = accountId(VERIFIED);
        Map<String, Object> order = jdbc.queryForMap("select * from rental_orders where id = ?", result.orderId());
        assertThat(order.get("request_id")).isEqualTo(requestId);
        assertThat(order.get("customer_id")).isEqualTo(customerId);
        assertThat(order.get("status")).isEqualTo("Pending");
        assertThat(order.get("unit_id")).isNull();
        assertThat(((java.sql.Timestamp) order.get("expires_at")).toInstant())
                .isCloseTo(OffsetDateTime.now().plusDays(30).toInstant(), within(1, ChronoUnit.MINUTES));

        Map<String, Object> proposal = jdbc.queryForMap("select * from proposal_feedbacks where id = ?",
                result.proposalId());
        assertThat(proposal.get("order_id")).isEqualTo(result.orderId());
        assertThat(proposal.get("customer_id")).isEqualTo(customerId);
        assertThat(proposal.get("unit_id")).isEqualTo(unitId("A-01"));
        assertThat(proposal.get("status")).isEqualTo("Pending");

        assertThat(requestStatus(requestId)).isEqualTo("Converted");
        assertApprovedNotification(customerId, result.orderId(), "A-01");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void approvalForUnverifiedAccountKeepsRequestApproved() {
        long requestId = approvedRequest(UNVERIFIED, "A-01", 7);

        ConversionResult result = service.convertOnApproval(requestId);

        assertThat(result.outcome()).isEqualTo(Outcome.NO_VERIFIED_ACCOUNT);
        assertThat(requestStatus(requestId)).isEqualTo("Approved");
        assertNothingCreated();
    }

    @Test
    void approvalForEmailWithoutAccountKeepsRequestApproved() {
        long requestId = approvedRequest("new.customer@example.com", "A-01", 7);

        assertThat(service.convertOnApproval(requestId).outcome()).isEqualTo(Outcome.NO_VERIFIED_ACCOUNT);
        assertThat(requestStatus(requestId)).isEqualTo("Approved");
        assertNothingCreated();
    }

    @Test
    void approvalOfUnknownRequestIsRejected() {
        assertThatThrownBy(() -> service.convertOnApproval(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvalOfExpiredRequestIsNotConverted() {
        long requestId = approvedRequest(VERIFIED, "A-01", -1);

        assertThat(service.convertOnApproval(requestId).outcome()).isEqualTo(Outcome.NOT_CONVERTIBLE);
        assertThat(requestStatus(requestId)).isEqualTo("Approved");
        assertNothingCreated();
    }

    @Test
    void approvalSkipsUnitAlreadyActiveForSameAccount() {
        long first = approvedRequest(VERIFIED, "A-01", 7);
        service.convertOnApproval(first);
        long duplicate = approvedRequest(VERIFIED, "A-01", 7);

        ConversionResult result = service.convertOnApproval(duplicate);

        assertThat(result.outcome()).isEqualTo(Outcome.DUPLICATE_UNIT);
        assertThat(requestStatus(duplicate)).isEqualTo("Approved");
        assertThat(count("rental_orders")).isEqualTo(1);
        assertThat(count("proposal_feedbacks")).isEqualTo(1);
    }

    @Test
    void approvalAllowsUnitAgainOnceEarlierOrderIsClosed() {
        long first = approvedRequest(VERIFIED, "A-01", 7);
        ConversionResult earlier = service.convertOnApproval(first);
        jdbc.update("update rental_orders set status = 'Canceled' where id = ?", earlier.orderId());
        long again = approvedRequest(VERIFIED, "A-01", 7);

        assertThat(service.convertOnApproval(again).outcome()).isEqualTo(Outcome.CONVERTED);
    }

    @Test
    void missingOrderExpiryPolicyRollsBackConversion() {
        jdbc.update("delete from policies where key = 'order.expiry_days'");
        long requestId = approvedRequest(VERIFIED, "A-01", 7);

        assertThatThrownBy(() -> service.convertOnApproval(requestId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("order.expiry_days");
        assertThat(requestStatus(requestId)).isEqualTo("Approved");
        assertNothingCreated();
    }

    @Test
    void emailFailureDoesNotRollBackConversion() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));
        long requestId = approvedRequest(VERIFIED, "A-01", 7);

        ConversionResult result = service.convertOnApproval(requestId);

        assertThat(result.outcome()).isEqualTo(Outcome.CONVERTED);
        verify(mailSender).send(any(MimeMessage.class));
        assertThat(requestStatus(requestId)).isEqualTo("Converted");
        assertThat(count("rental_orders")).isEqualTo(1);
        assertApprovedNotification(accountId(VERIFIED), result.orderId(), "A-01");
    }

    // ------------------------------------------------------------ trigger 2: claim sau xác minh

    @Test
    void claimBeforeVerificationDoesNothingAndClaimAfterVerificationConverts() {
        long requestId = approvedRequest(UNVERIFIED, "B-01", 7);
        long accountId = accountId(UNVERIFIED);

        assertThat(service.claimForVerifiedAccount(accountId)).isEmpty();
        assertThat(requestStatus(requestId)).isEqualTo("Approved");
        assertNothingCreated();

        verifyEmail(UNVERIFIED);
        List<ConversionResult> results = service.claimForVerifiedAccount(accountId);

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.outcome()).isEqualTo(Outcome.CONVERTED);
            assertThat(r.requestId()).isEqualTo(requestId);
        });
        long orderId = results.get(0).orderId();
        assertThat(requestStatus(requestId)).isEqualTo("Converted");
        assertThat(jdbc.queryForObject("select customer_id from rental_orders where id = ?", Long.class, orderId))
                .isEqualTo(accountId);
        assertApprovedNotification(accountId, orderId, "B-01");
        Map<String, Object> audit = jdbc.queryForMap("select * from audit_logs where entity_id = ?",
                String.valueOf(requestId));
        assertThat(audit.get("action")).isEqualTo("RENTAL_REQUEST_CLAIMED");
        assertThat(audit.get("entity_type")).isEqualTo("RentalRequest");
        assertThat(audit.get("actor_account_id")).isEqualTo(accountId);
        assertThat(String.valueOf(audit.get("new_value"))).contains("Converted").contains(String.valueOf(orderId));
    }

    @Test
    void claimIgnoresExpiredAndNonApprovedRequests() {
        verifyEmail(UNVERIFIED);
        long expired = approvedRequest(UNVERIFIED, "A-01", -1);
        long pending = request(UNVERIFIED, "A-02", "Pending", 7);
        long valid = approvedRequest(UNVERIFIED, "A-03", 7);

        List<ConversionResult> results = service.claimForVerifiedAccount(accountId(UNVERIFIED));

        assertThat(results).extracting(ConversionResult::requestId).containsExactly(valid);
        assertThat(requestStatus(expired)).isEqualTo("Approved");
        assertThat(requestStatus(pending)).isEqualTo("Pending");
        assertThat(requestStatus(valid)).isEqualTo("Converted");
    }

    @Test
    void claimCreatesOneConversionPerUnitAndKeepsDuplicatesApproved() {
        verifyEmail(UNVERIFIED);
        long first = approvedRequest(UNVERIFIED, "A-02", 7);
        long duplicate = approvedRequest(UNVERIFIED, "A-02", 7);
        long otherUnit = approvedRequest(UNVERIFIED, "A-03", 7);

        List<ConversionResult> results = service.claimForVerifiedAccount(accountId(UNVERIFIED));

        assertThat(results).extracting(ConversionResult::requestId, ConversionResult::outcome).containsExactly(
                org.assertj.core.groups.Tuple.tuple(first, Outcome.CONVERTED),
                org.assertj.core.groups.Tuple.tuple(duplicate, Outcome.DUPLICATE_UNIT),
                org.assertj.core.groups.Tuple.tuple(otherUnit, Outcome.CONVERTED));
        assertThat(requestStatus(duplicate)).isEqualTo("Approved");
        assertThat(count("rental_orders")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from proposal_feedbacks where unit_id = ?", Long.class,
                unitId("A-02"))).isEqualTo(1);
    }

    @Test
    void claimForUnknownAccountDoesNothing() {
        assertThat(service.claimForVerifiedAccount(-1L)).isEmpty();
    }

    // ------------------------------------------------------------ đồng thời

    @Test
    void concurrentClaimsCreateOnlyOneConversionForSameAccountAndUnit() throws Exception {
        verifyEmail(UNVERIFIED);
        long accountId = accountId(UNVERIFIED);
        approvedRequest(UNVERIFIED, "B-02", 7);
        approvedRequest(UNVERIFIED, "B-02", 7);

        List<Object> results = runConcurrently(4, () -> service.claimForVerifiedAccount(accountId));

        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(List.class));
        assertOneActiveConversion("B-02");
    }

    @Test
    void concurrentApprovalAndClaimCreateOnlyOneConversionForSameUnit() throws Exception {
        verifyEmail(UNVERIFIED);
        long accountId = accountId(UNVERIFIED);
        long approvedByFm = approvedRequest(UNVERIFIED, "B-03", 7);
        approvedRequest(UNVERIFIED, "B-03", 7);

        List<Callable<Object>> tasks = List.of(
                () -> service.convertOnApproval(approvedByFm),
                () -> service.claimForVerifiedAccount(accountId));
        List<Object> results = runConcurrently(tasks);

        assertThat(results).noneMatch(r -> r instanceof Throwable);
        assertOneActiveConversion("B-03");
    }

    /**
     * Race thật giữa hai trigger: FM duyệt r1 trong transaction B1 chưa commit (với transaction khác
     * r1 vẫn {@code Pending}, nên claim không khoá trùng row nào) đúng lúc khách xác minh và claim r2
     * cùng khoang. Chỉ khoá theo account mới bắt claim chờ và thấy đơn của r1.
     */
    @Test
    void claimDuringUncommittedApprovalWaitsAndSkipsSameUnit() throws Exception {
        verifyEmail(UNVERIFIED);
        long accountId = accountId(UNVERIFIED);
        long beingApproved = request(UNVERIFIED, "B-01", "Pending", 7);
        long alreadyApproved = approvedRequest(UNVERIFIED, "B-01", 7);
        CountDownLatch converted = new CountDownLatch(1);

        List<Callable<Object>> tasks = List.of(
                () -> new TransactionTemplate(transactionManager).execute(status -> {
                    // Giả lập B1: chuyển Approved rồi convert trong cùng transaction, giữ chưa commit.
                    jdbc.update("update rental_requests set status = 'Approved' where id = ?", beingApproved);
                    ConversionResult result = service.convertOnApproval(beingApproved);
                    converted.countDown();
                    sleep(1500);
                    return result;
                }),
                () -> {
                    converted.await(10, TimeUnit.SECONDS);
                    return service.claimForVerifiedAccount(accountId);
                });
        List<Object> results = runConcurrently(tasks);

        assertThat(results).noneMatch(r -> r instanceof Throwable);
        assertThat(((ConversionResult) results.get(0)).outcome()).isEqualTo(Outcome.CONVERTED);
        assertThat(requestStatus(alreadyApproved)).isEqualTo("Approved");
        assertOneActiveConversion("B-01");
    }

    // ------------------------------------------------------------ cron: request hết hạn

    @Test
    void expiryJobExpiresOverduePendingAndApprovedRequestsOnly() {
        long pendingOverdue = request(VERIFIED, "A-01", "Pending", -1);
        long approvedOverdue = request(UNVERIFIED, "A-02", "Approved", -1);
        long approvedValid = request(UNVERIFIED, "A-03", "Approved", 3);
        long convertedOverdue = request(VERIFIED, "A-04", "Converted", -1);

        expiryJob.run();

        assertThat(requestStatus(pendingOverdue)).isEqualTo("Expired");
        assertThat(requestStatus(approvedOverdue)).isEqualTo("Expired");
        assertThat(requestStatus(approvedValid)).isEqualTo("Approved");
        assertThat(requestStatus(convertedOverdue)).isEqualTo("Converted");
        assertThat(expiryService.expireOverdue()).as("chạy lại không đổi gì thêm").isZero();
    }

    @Test
    void expiredRequestCannotBeClaimedAfterVerification() {
        long requestId = approvedRequest(UNVERIFIED, "A-01", -1);
        expiryService.expireOverdue();
        verifyEmail(UNVERIFIED);

        assertThat(service.claimForVerifiedAccount(accountId(UNVERIFIED))).isEmpty();
        assertThat(requestStatus(requestId)).isEqualTo("Expired");
        assertNothingCreated();
    }

    // ------------------------------------------------------------ helpers

    private void assertOneActiveConversion(String unitCode) {
        assertThat(count("rental_orders")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from proposal_feedbacks where unit_id = ? and status = 'Pending'",
                Long.class, unitId(unitCode))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from rental_requests where status = 'Converted'", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from rental_requests where status = 'Approved'", Long.class))
                .isEqualTo(1);
        assertThat(count("notifications")).isEqualTo(1);
    }

    private void assertApprovedNotification(long accountId, long orderId, String unitCode) {
        Map<String, Object> row = jdbc.queryForMap("select * from notifications where account_id = ?", accountId);
        assertThat(row.get("type")).isEqualTo("RENTAL_REQUEST_APPROVED");
        assertThat(row.get("title")).isEqualTo("Yêu cầu thuê kho đã được duyệt");
        assertThat((String) row.get("body")).contains(unitCode).contains("http://fe.test/proposals");
        assertThat(jdbc.queryForObject("select order_id from order_notifications where notification_id = ?",
                Long.class, row.get("id"))).isEqualTo(orderId);
    }

    private void assertNothingCreated() {
        assertThat(count("rental_orders")).isZero();
        assertThat(count("proposal_feedbacks")).isZero();
        assertThat(count("notifications")).isZero();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private List<Object> runConcurrently(int threads, Callable<Object> task) throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(task);
        }
        return runConcurrently(tasks);
    }

    private List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private long approvedRequest(String email, String unitCode, int expiresInDays) {
        return request(email, unitCode, "Approved", expiresInDays);
    }

    private long request(String email, String unitCode, String status, int expiresInDays) {
        Long facilityId = jdbc.queryForObject("select facility_id from storage_units where code = ?", Long.class,
                unitCode);
        Long unitTypeId = jdbc.queryForObject("select unit_type_id from storage_units where code = ?", Long.class,
                unitCode);
        return jdbc.queryForObject("""
                insert into rental_requests (facility_id, customer_name, normalized_customer_email, customer_phone,
                    unit_type_id, start_date, period, unit_id, status, responded_at, expires_at)
                values (?, 'Khách Test', ?, '0900000000', ?, current_date + 7, 3, ?, ?, now(),
                    now() + make_interval(days => ?))
                returning id""", Long.class, facilityId, email, unitTypeId, unitId(unitCode), status, expiresInDays);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void verifyEmail(String email) {
        jdbc.update("update accounts set email_verified_at = now() where email = ?", email);
    }

    private long accountId(String email) {
        return jdbc.queryForObject("select id from accounts where email = ?", Long.class, email);
    }

    private long unitId(String code) {
        return jdbc.queryForObject("select id from storage_units where code = ?", Long.class, code);
    }

    private String requestStatus(long requestId) {
        return jdbc.queryForObject("select status from rental_requests where id = ?", String.class, requestId);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
