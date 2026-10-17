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
import org.springframework.context.ApplicationEventPublisher;
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
import vn.lemar.selfstorage.booking.application.RentalRequestService;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.identity.application.EmailVerified;

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
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private RentalRequestService rentalRequestService;
    @Autowired
    private ApplicationEventPublisher events;
    // Gọi approve của B1 không qua HTTP: bỏ guard RBAC + JWT, chỉ giữ actor cho audit.
    @MockBean
    private Access access;
    @MockBean
    private CurrentAccountProvider currentAccountProvider;

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
     * cùng khoang. Chỉ khoá advisory theo email mới bắt claim chờ và thấy đơn của r1. Transaction
     * duyệt chỉ commit sau khi thấy claim thật sự bị chặn trên advisory lock — không có khoá thì
     * {@link #awaitBlockedOnAdvisoryLock()} hết hạn và test fail.
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
                    awaitBlockedOnAdvisoryLock();
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

    /**
     * Review #149 mục 1: FM duyệt lúc account chưa xác minh xong (tx duyệt tra account → chưa xác
     * minh → {@code NO_VERIFIED_ACCOUNT}), rồi xác minh commit và claim chạy trước khi tx duyệt
     * commit. Khoá theo email bắt claim chờ duyệt commit, nên select thấy request {@code Approved} và
     * claim được — không kẹt {@code Approved}. Khoá theo account id thì nhánh chưa xác minh không giữ
     * khoá nào, claim không bị chặn và {@link #awaitBlockedOnAdvisoryLock()} hết hạn.
     */
    @Test
    void approvalBeforeVerificationCommitIsStillClaimed() throws Exception {
        long accountId = accountId(UNVERIFIED);
        long requestId = request(UNVERIFIED, "B-02", "Pending", 7);
        CountDownLatch looked = new CountDownLatch(1);

        List<Callable<Object>> tasks = List.of(
                () -> new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.update("update rental_requests set status = 'Approved' where id = ?", requestId);
                    ConversionResult result = service.convertOnApproval(requestId);
                    looked.countDown();
                    awaitBlockedOnAdvisoryLock();
                    return result;
                }),
                () -> {
                    looked.await(10, TimeUnit.SECONDS);
                    verifyEmail(UNVERIFIED); // commit ngay (auto-commit) — như tx xác minh vừa xong
                    return service.claimForVerifiedAccount(accountId);
                });
        List<Object> results = runConcurrently(tasks);

        assertThat(results).noneMatch(r -> r instanceof Throwable);
        assertThat(((ConversionResult) results.get(0)).outcome()).isEqualTo(Outcome.NO_VERIFIED_ACCOUNT);
        @SuppressWarnings("unchecked")
        List<ConversionResult> claimed = (List<ConversionResult>) results.get(1);
        assertThat(claimed).extracting(ConversionResult::requestId, ConversionResult::outcome)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(requestId, Outcome.CONVERTED));
        assertThat(requestStatus(requestId)).isEqualTo("Converted");
    }

    // ------------------------------------------------------------ nối B1 + A3a

    @Test
    void fmApprovalThroughB1ConvertsForVerifiedCustomer() {
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(accountId("fm1@lemar.vn"));
        long requestId = request(VERIFIED, "A-01", "Pending", 7);

        rentalRequestService.approve(requestId, unitId("A-01"));

        assertThat(requestStatus(requestId)).isEqualTo("Converted");
        assertThat(count("rental_orders")).isEqualTo(1);
        assertThat(jdbc.queryForList("select action from audit_logs where entity_id = ?", String.class,
                String.valueOf(requestId))).containsExactly("RENTAL_REQUEST_APPROVED");
        Long orderId = jdbc.queryForObject("select id from rental_orders", Long.class);
        assertApprovedNotification(accountId(VERIFIED), orderId, "A-01");
    }

    @Test
    void fmApprovalThroughB1KeepsApprovedForUnverifiedCustomer() {
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(accountId("fm1@lemar.vn"));
        long requestId = request(UNVERIFIED, "A-01", "Pending", 7);

        rentalRequestService.approve(requestId, unitId("A-01"));

        assertThat(requestStatus(requestId)).isEqualTo("Approved");
        assertNothingCreated();
    }

    @Test
    void emailVerifiedEventClaimsAfterVerificationCommits() {
        long requestId = approvedRequest(UNVERIFIED, "B-01", 7);
        long accountId = accountId(UNVERIFIED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            verifyEmail(UNVERIFIED);
            events.publishEvent(new EmailVerified(accountId));
            assertThat(requestStatus(requestId)).as("chưa claim trước commit").isEqualTo("Approved");
        });

        assertThat(requestStatus(requestId)).isEqualTo("Converted");
        assertThat(jdbc.queryForObject("select action from audit_logs where entity_id = ?", String.class,
                String.valueOf(requestId))).isEqualTo("RENTAL_REQUEST_CLAIMED");
    }

    @Test
    void emailVerifiedEventDoesNothingWhenVerificationRollsBack() {
        long requestId = approvedRequest(UNVERIFIED, "B-01", 7);
        long accountId = accountId(UNVERIFIED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            verifyEmail(UNVERIFIED);
            events.publishEvent(new EmailVerified(accountId));
            status.setRollbackOnly();
        });

        assertThat(requestStatus(requestId)).isEqualTo("Approved");
        assertNothingCreated();
    }

    @Test
    void claimFailureAfterVerificationDoesNotUndoVerification() {
        jdbc.update("delete from policies where key = 'order.expiry_days'");
        long requestId = approvedRequest(UNVERIFIED, "B-01", 7);
        long accountId = accountId(UNVERIFIED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            verifyEmail(UNVERIFIED);
            events.publishEvent(new EmailVerified(accountId));
        });

        assertThat(jdbc.queryForObject("select email_verified_at is not null from accounts where id = ?",
                Boolean.class, accountId)).isTrue();
        assertThat(requestStatus(requestId)).isEqualTo("Approved");
        assertNothingCreated();
    }

    // ------------------------------------------------------------ request đã Expired

    @Test
    void expiredRequestCannotBeClaimedAfterVerification() {
        // Chuyển Expired thuộc cron Flow 1 (#143) — ở đây đặt trạng thái trực tiếp.
        long requestId = request(UNVERIFIED, "A-01", "Expired", -1);
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

    /**
     * Chờ tới khi có một session đang bị chặn trên advisory lock (claim chờ transaction đang giữ khoá)
     * — thay cho sleep cố định: CI chậm vẫn tạo đúng interleaving, còn nếu không có ai bị chặn trong
     * 10 giây thì ném lỗi để test fail.
     */
    private void awaitBlockedOnAdvisoryLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long waiting = jdbc.queryForObject(
                    "select count(*) from pg_locks where locktype = 'advisory' and not granted", Long.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Không có session nào chờ advisory lock trong 10 giây");
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
