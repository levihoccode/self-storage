package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.booking.application.AgreeResult;
import vn.lemar.selfstorage.booking.application.ProposalService;
import vn.lemar.selfstorage.booking.application.ProposalView;
import vn.lemar.selfstorage.booking.application.exception.ProposalConflictException;
import vn.lemar.selfstorage.booking.application.exception.ProposalForbiddenException;
import vn.lemar.selfstorage.booking.application.exception.ProposalNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.ProposalValidationException;
import vn.lemar.selfstorage.booking.application.exception.RentalOrderNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitNotFoundException;
import vn.lemar.selfstorage.identity.application.exception.ForbiddenException;

/**
 * Flow 1.3 chạy thật trên Postgres (Testcontainers) với seed V2/V3: khách đồng ý / từ chối, FM đề
 * xuất lại. Đơn + proposal do luồng B2 tạo nên test tự chèn bằng SQL. Máy không có Docker thì skip.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ProposalServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProposalService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private long customerId;
    private long otherCustomerId;
    private long fmId;
    private long facilityId;
    private long orderId;

    @BeforeEach
    void seedOrder() {
        customerId = accountId("customer1@lemar.vn");
        otherCustomerId = accountId("unverified@lemar.vn");
        fmId = accountId("fm1@lemar.vn");
        facilityId = jdbc.queryForObject("select id from facilities where code = 'Q7'", Long.class);
        Long smallType = jdbc.queryForObject("select id from unit_types where name = 'Small'", Long.class);
        Long requestId = jdbc.queryForObject("""
                insert into rental_requests (facility_id, customer_name, normalized_customer_email, customer_phone,
                    unit_type_id, start_date, period, unit_id, status)
                values (?, 'Khách Test', 'customer1@lemar.vn', '0900000000', ?, current_date + 7, 3, ?, 'Converted')
                returning id""", Long.class, facilityId, smallType, unitId("A-01"));
        orderId = jdbc.queryForObject("""
                insert into rental_orders (request_id, customer_id, status, expires_at)
                values (?, ?, 'Pending', now() + interval '30 days') returning id""",
                Long.class, requestId, customerId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- đồng ý

    @Test
    void agreeMarksAgreedAssignsUnitAndAudits() {
        long proposalId = proposal("A-01", "Pending");

        AgreeResult result = service.agree(proposalId, customerId);

        assertThat(result.outcome()).isEqualTo(AgreeResult.Outcome.AGREED);
        assertThat(result.proposal().status()).isEqualTo("Agreed");
        assertThat(proposalStatus(proposalId)).isEqualTo("Agreed");
        assertThat(orderUnitId()).isEqualTo(unitId("A-01"));
        assertThat(auditActions(proposalId)).containsExactly("PROPOSAL_AGREED");
        assertThat(jdbc.queryForObject("select actor_account_id from audit_logs where entity_id = ?", Long.class,
                String.valueOf(proposalId))).isEqualTo(customerId);
    }

    @Test
    void agreeOnUnavailableUnitExpiresProposalAndAsksFmToRepropose() {
        long proposalId = proposal("C-01", "Pending"); // C-01 đang Maintenance trong seed

        AgreeResult result = service.agree(proposalId, customerId);

        assertThat(result.outcome()).isEqualTo(AgreeResult.Outcome.UNIT_UNAVAILABLE);
        assertThat(proposalStatus(proposalId)).isEqualTo("Expired");
        assertThat(orderUnitId()).isNull();
        assertThat(notificationsOf(fmId)).singleElement().satisfies(row -> {
            assertThat(row.get("type")).isEqualTo("PROPOSAL_REPROPOSAL_REQUIRED");
            assertThat((String) row.get("body")).contains("Đơn " + orderId).contains("C-01");
        });
        assertThat(orderNotificationCount()).isEqualTo(1);
    }

    @Test
    void agreeByAnotherCustomerIsForbidden() {
        long proposalId = proposal("A-01", "Pending");

        assertThatThrownBy(() -> service.agree(proposalId, otherCustomerId))
                .isInstanceOf(ProposalForbiddenException.class);
        assertThat(proposalStatus(proposalId)).isEqualTo("Pending");
    }

    @Test
    void agreeOnAlreadyHandledProposalConflicts() {
        long proposalId = proposal("A-01", "Rejected");

        assertThatThrownBy(() -> service.agree(proposalId, customerId))
                .isInstanceOf(ProposalConflictException.class);
    }

    @Test
    void agreeOnUnknownProposalIsNotFound() {
        assertThatThrownBy(() -> service.agree(-1L, customerId)).isInstanceOf(ProposalNotFoundException.class);
    }

    // ---------------------------------------------------------------- từ chối

    @Test
    void rejectStoresNoteAuditsAndNotifiesFm() {
        long proposalId = proposal("A-01", "Pending");

        ProposalView view = service.reject(proposalId, customerId, "  Khoang xa cửa ra vào  ");

        assertThat(view.status()).isEqualTo("Rejected");
        assertThat(view.note()).isEqualTo("Khoang xa cửa ra vào");
        assertThat(auditActions(proposalId)).containsExactly("PROPOSAL_REJECTED");
        assertThat(notificationsOf(fmId)).singleElement().satisfies(row -> {
            assertThat(row.get("type")).isEqualTo("PROPOSAL_REJECTED");
            assertThat((String) row.get("body")).contains("A-01").contains("Khoang xa cửa ra vào");
        });
    }

    @Test
    void rejectWithoutNoteIsInvalidAndChangesNothing() {
        long proposalId = proposal("A-01", "Pending");

        assertThatThrownBy(() -> service.reject(proposalId, customerId, "   "))
                .isInstanceOf(ProposalValidationException.class);
        assertThatThrownBy(() -> service.reject(proposalId, customerId, null))
                .isInstanceOf(ProposalValidationException.class);
        assertThat(proposalStatus(proposalId)).isEqualTo("Pending");
    }

    @Test
    void rejectWhenFacilityHasNoFmSkipsNotification() {
        long proposalId = proposal("A-01", "Pending");
        jdbc.update("update facilities set fm_account_id = null where id = ?", facilityId);

        service.reject(proposalId, customerId, "Đổi ý");

        assertThat(proposalStatus(proposalId)).isEqualTo("Rejected");
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from notifications", Long.class)).isZero();
    }

    // ---------------------------------------------------------------- đề xuất lại (FM)

    @Test
    void reproposeCreatesPendingProposalAndNotifiesCustomer() {
        proposal("A-01", "Rejected");
        loginAs("fm1@lemar.vn");

        ProposalView view = service.repropose(orderId, unitId("A-02"), null, fmId);

        assertThat(view.status()).isEqualTo("Pending");
        assertThat(view.unitId()).isEqualTo(unitId("A-02"));
        assertThat(view.customerId()).isEqualTo(customerId);
        assertThat(auditActions(view.id())).containsExactly("PROPOSAL_REPROPOSED");
        assertThat(auditReason(view.id())).isNull();
        assertThat(notificationsOf(customerId)).singleElement().satisfies(row -> {
            assertThat(row.get("type")).isEqualTo("PROPOSAL_REPROPOSED");
            assertThat((String) row.get("body")).contains("http://localhost:5173/proposals");
        });
    }

    @Test
    void reproposeRejectedUnitRequiresOverrideReason() {
        proposal("A-01", "Rejected");
        loginAs("fm1@lemar.vn");

        assertThatThrownBy(() -> service.repropose(orderId, unitId("A-01"), " ", fmId))
                .isInstanceOf(ProposalValidationException.class)
                .hasMessageContaining("A-01");
    }

    @Test
    void reproposeRejectedUnitWithReasonIsAuditedAsOverride() {
        proposal("A-01", "Rejected");
        loginAs("fm1@lemar.vn");

        ProposalView view = service.repropose(orderId, unitId("A-01"), "Khách gọi lại muốn lấy khoang này", fmId);

        assertThat(view.unitId()).isEqualTo(unitId("A-01"));
        assertThat(auditReason(view.id())).isEqualTo("Khách gọi lại muốn lấy khoang này");
    }

    @Test
    void reproposeWhileAProposalIsPendingConflicts() {
        proposal("A-01", "Pending");
        loginAs("fm1@lemar.vn");

        assertThatThrownBy(() -> service.repropose(orderId, unitId("A-02"), null, fmId))
                .isInstanceOf(ProposalConflictException.class);
    }

    @Test
    void reproposeUnavailableUnitConflicts() {
        proposal("A-01", "Rejected");
        loginAs("fm1@lemar.vn");

        assertThatThrownBy(() -> service.repropose(orderId, unitId("C-01"), null, fmId))
                .isInstanceOf(ProposalConflictException.class)
                .hasMessageContaining("C-01");
    }

    @Test
    void reproposeUnitOfAnotherFacilityIsNotFound() {
        Long tdUnit = jdbc.queryForObject("""
                insert into storage_units (code, facility_id, unit_type_id, status)
                values ('TD-01', (select id from facilities where code = 'TD'),
                        (select id from unit_types where name = 'Small'), 'Available') returning id""", Long.class);
        loginAs("fm1@lemar.vn");

        assertThatThrownBy(() -> service.repropose(orderId, tdUnit, null, fmId))
                .isInstanceOf(StorageUnitNotFoundException.class);
    }

    @Test
    void reproposeOnClosedOrderConflicts() {
        jdbc.update("update rental_orders set status = 'Canceled', cancel_reason = 'Khách không còn nhu cầu' "
                + "where id = ?", orderId);
        loginAs("fm1@lemar.vn");

        assertThatThrownBy(() -> service.repropose(orderId, unitId("A-02"), null, fmId))
                .isInstanceOf(ProposalConflictException.class);
    }

    @Test
    void reproposeByFmOfAnotherFacilityIsForbidden() {
        loginAs("fm2@lemar.vn");

        assertThatThrownBy(() -> service.repropose(orderId, unitId("A-02"), null, accountId("fm2@lemar.vn")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void reproposeOnUnknownOrderIsNotFound() {
        loginAs("fm1@lemar.vn");

        assertThatThrownBy(() -> service.repropose(-1L, unitId("A-02"), null, fmId))
                .isInstanceOf(RentalOrderNotFoundException.class);
    }

    // ---------------------------------------------------------------- helpers

    private long proposal(String unitCode, String status) {
        return jdbc.queryForObject("""
                insert into proposal_feedbacks (order_id, customer_id, unit_id, status, note)
                values (?, ?, ?, ?, ?) returning id""", Long.class,
                orderId, customerId, unitId(unitCode), status, "Rejected".equals(status) ? "Không hợp" : null);
    }

    private long accountId(String email) {
        return jdbc.queryForObject("select id from accounts where email = ?", Long.class, email);
    }

    private long unitId(String code) {
        return jdbc.queryForObject("select id from storage_units where code = ?", Long.class, code);
    }

    private String proposalStatus(long proposalId) {
        entityManager.flush();
        return jdbc.queryForObject("select status from proposal_feedbacks where id = ?", String.class, proposalId);
    }

    private List<String> auditActions(long proposalId) {
        entityManager.flush();
        return jdbc.queryForList("select action from audit_logs where entity_type = 'ProposalFeedback' "
                + "and entity_id = ? order by id", String.class, String.valueOf(proposalId));
    }

    private String auditReason(long proposalId) {
        entityManager.flush();
        return jdbc.queryForObject("select reason from audit_logs where entity_id = ?", String.class,
                String.valueOf(proposalId));
    }

    private List<Map<String, Object>> notificationsOf(long accountId) {
        entityManager.flush();
        return jdbc.queryForList("select type, body from notifications where account_id = ?", accountId);
    }

    private Long orderUnitId() {
        entityManager.flush();
        return jdbc.queryForObject("select unit_id from rental_orders where id = ?", Long.class, orderId);
    }

    private long orderNotificationCount() {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from order_notifications where order_id = ?", Long.class, orderId);
    }

    private static void loginAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(email, null, List.of()));
    }
}
