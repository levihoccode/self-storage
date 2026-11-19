package vn.lemar.selfstorage.handover.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.booking.application.OrderNotificationService;
import vn.lemar.selfstorage.handover.application.exception.HandoverConflictException;
import vn.lemar.selfstorage.handover.application.exception.HandoverNotFoundException;
import vn.lemar.selfstorage.handover.application.exception.HandoverValidationException;
import vn.lemar.selfstorage.handover.domain.UnitOverlock;
import vn.lemar.selfstorage.handover.repository.UnitOverlockRepository;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.notification.application.NotificationRecipient;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationType;

/**
 * Khoá chặn của cơ sở (overlock) cho khoang dùng chìa cơ khi hợp đồng quá hạn kéo dài bị tạm khoá truy cập.
 * <ul>
 *   <li>Tạm khoá ({@link RentalContractService#suspendAccess}) + hợp đồng còn chìa cơ {@code Active} → yêu cầu gắn
 *       ({@code Required}); FS cơ sở + FM nhận {@code OVERLOCK_REQUIRED}.</li>
 *   <li>FS gắn tại khoang → {@code Applied}.</li>
 *   <li>Mở lại truy cập ({@link RentalContractService#restoreAccess}) → chưa gắn thì {@code Canceled}; đã gắn thì
 *       {@code RemovalRequired}, FS cơ sở + FM nhận {@code OVERLOCK_REMOVAL_REQUIRED}.</li>
 *   <li>FS gỡ tại khoang → {@code Removed}; khách nhận {@code OVERLOCK_REMOVED}.</li>
 * </ul>
 * FS thao tác cần {@code unit_access_key.issue} tại cơ sở của khoang (cùng quyền giao / thu hồi chìa).
 */
@Service
public class UnitOverlockService {

    static final String PERM = "unit_access_key.issue";
    static final int NOTE_MAX = 500;

    private static final Logger LOG = LoggerFactory.getLogger(UnitOverlockService.class);

    /** Khoá chặn kèm thông tin hợp đồng / khoang để FS, FM hiển thị. */
    public record OverlockView(Long id, Long contractId, String contractCode, Long orderId, Long unitId,
                               String unitCode, Long facilityId, String facilityName, String status,
                               Instant requiredAt, Instant appliedAt, Instant removalRequestedAt, Instant removedAt,
                               String note) {

        /** Truy cập đã mở lại, khoá chặn còn trên khoang — chờ FS gỡ. */
        public boolean removalPending() {
            return UnitOverlock.REMOVAL_REQUIRED.equals(status);
        }

        /** Khoá chặn đang gắn trên khoang (đã gắn hoặc chờ gỡ). */
        public boolean onUnit() {
            return UnitOverlock.APPLIED.equals(status) || UnitOverlock.REMOVAL_REQUIRED.equals(status);
        }
    }

    private final UnitOverlockRepository overlocks;
    private final JdbcTemplate jdbc;
    private final Access access;
    private final CurrentAccountProvider currentAccount;
    private final AuditRecorder auditRecorder;
    private final NotificationService notificationService;
    private final OrderNotificationService orderNotifications;

    UnitOverlockService(UnitOverlockRepository overlocks, JdbcTemplate jdbc, Access access,
                        CurrentAccountProvider currentAccount, AuditRecorder auditRecorder,
                        NotificationService notificationService, OrderNotificationService orderNotifications) {
        this.overlocks = overlocks;
        this.jdbc = jdbc;
        this.access = access;
        this.currentAccount = currentAccount;
        this.auditRecorder = auditRecorder;
        this.notificationService = notificationService;
        this.orderNotifications = orderNotifications;
    }

    /** Việc khoá chặn đang mở ở các cơ sở FS đang đăng nhập được gán — chờ gỡ trước, rồi chờ gắn, rồi đang gắn. */
    @Transactional(readOnly = true)
    public List<OverlockView> staffQueue() {
        Long staffId = currentAccount.getCurrentAccountId();
        List<Long> facilityIds = jdbc.queryForList(
                "select facility_id from account_facility_assignments where account_id = ?", Long.class, staffId);
        facilityIds.forEach(facilityId -> access.can(PERM, facilityId));
        if (facilityIds.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", facilityIds.stream().map(id -> "?").toList());
        return jdbc.query(SELECT + " where " + OPEN + " and u.facility_id in (" + in + ") order by case o.status "
                        + "when 'RemovalRequired' then 0 when 'Required' then 1 else 2 end, o.required_at",
                (rs, i) -> map(rs), facilityIds.toArray());
    }

    /** Khoá chặn đang mở của hợp đồng (biên bản trả kho, danh sách quá hạn của FM). */
    @Transactional(readOnly = true)
    public Optional<OverlockView> openOf(Long contractId) {
        return jdbc.query(SELECT + " where o.contract_id = ? and " + OPEN, (rs, i) -> map(rs), contractId).stream()
                .findFirst();
    }

    /** FS xác nhận đã gắn khoá chặn tại khoang. */
    @Transactional
    public OverlockView apply(Long overlockId, String note) {
        UnitOverlock lock = lockForStaff(overlockId);
        if (!UnitOverlock.REQUIRED.equals(lock.getStatus())) {
            throw new HandoverConflictException("Khoá chặn không ở trạng thái chờ gắn (" + lock.getStatus() + ")");
        }
        Long staffId = currentAccount.getCurrentAccountId();
        lock.apply(staffId, trimNote(note), Instant.now());
        audit(AuditAction.OVERLOCK_APPLIED, lock, UnitOverlock.REQUIRED, staffId);
        return view(lock.getId());
    }

    /** FS xác nhận đã gỡ khoá chặn tại khoang; khách nhận thông báo vào lại khoang bằng chìa của mình. */
    @Transactional
    public OverlockView remove(Long overlockId, String note) {
        UnitOverlock lock = lockForStaff(overlockId);
        if (!UnitOverlock.REMOVAL_REQUIRED.equals(lock.getStatus())) {
            throw new HandoverConflictException(UnitOverlock.APPLIED.equals(lock.getStatus())
                    ? "Hợp đồng vẫn đang tạm khoá truy cập — chưa gỡ khoá chặn được"
                    : "Khoá chặn không ở trạng thái chờ gỡ (" + lock.getStatus() + ")");
        }
        Long staffId = currentAccount.getCurrentAccountId();
        lock.remove(staffId, trimNote(note), Instant.now());
        audit(AuditAction.OVERLOCK_REMOVED, lock, UnitOverlock.REMOVAL_REQUIRED, staffId);
        OverlockView view = view(lock.getId());
        orderNotifications.notifyCustomer(view.orderId(), NotificationType.OVERLOCK_REMOVED, values(view));
        return view;
    }

    /** Tạm khoá truy cập mà hợp đồng còn chìa cơ: yêu cầu gắn khoá chặn. Đã có khoá chặn mở thì giữ nguyên. */
    @Transactional(propagation = Propagation.MANDATORY)
    void requireOnSuspend(Long contractId, Long unitId, Long actorAccountId) {
        if (overlocks.findOpenForUpdate(contractId).isPresent()) {
            return;
        }
        UnitOverlock lock = overlocks.saveAndFlush(UnitOverlock.require(contractId, unitId, Instant.now()));
        audit(AuditAction.OVERLOCK_REQUIRED, lock, null, actorAccountId);
        notifyWork(view(lock.getId()), NotificationType.OVERLOCK_REQUIRED);
    }

    /** Mở lại truy cập: huỷ yêu cầu chưa gắn, hoặc yêu cầu FS gỡ khoá chặn đã gắn. Trả trạng thái sau cùng (nếu có). */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<String> releaseOnRestore(Long contractId, Long actorAccountId) {
        UnitOverlock lock = overlocks.findOpenForUpdate(contractId).orElse(null);
        if (lock == null) {
            return Optional.empty();
        }
        String before = lock.getStatus();
        boolean needsRemoval = lock.release(Instant.now());
        if (!before.equals(lock.getStatus())) {
            audit(needsRemoval ? AuditAction.OVERLOCK_REMOVAL_REQUIRED : AuditAction.OVERLOCK_CANCELED, lock, before,
                    actorAccountId);
            if (needsRemoval) {
                notifyWork(view(lock.getId()), NotificationType.OVERLOCK_REMOVAL_REQUIRED);
            }
        }
        return Optional.of(lock.getStatus());
    }

    /**
     * Chốt trả kho: khoá chặn còn gắn (chưa gỡ) chặn chốt — FS gỡ trước. Yêu cầu gắn chưa thực hiện thì huỷ (hợp
     * đồng kết thúc, khoang không cần gắn nữa). Trả thông báo lỗi khi còn khoá chặn trên khoang.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> settleForCheckout(Long contractId, Long actorAccountId) {
        UnitOverlock lock = overlocks.findOpenForUpdate(contractId).orElse(null);
        if (lock == null) {
            return Optional.empty();
        }
        if (UnitOverlock.REQUIRED.equals(lock.getStatus())) {
            lock.release(Instant.now());
            audit(AuditAction.OVERLOCK_CANCELED, lock, UnitOverlock.REQUIRED, actorAccountId);
            return Optional.empty();
        }
        return Optional.of("Khoang còn khoá chặn của cơ sở — gỡ khoá chặn trước khi chốt trả kho");
    }

    private UnitOverlock lockForStaff(Long overlockId) {
        UnitOverlock lock = overlocks.findByIdForUpdate(overlockId)
                .orElseThrow(() -> new HandoverNotFoundException("Không tìm thấy khoá chặn"));
        access.can(PERM, view(lock.getId()).facilityId());
        return lock;
    }

    private OverlockView view(Long overlockId) {
        overlocks.flush();
        return jdbc.query(SELECT + " where o.id = ?", (rs, i) -> map(rs), overlockId).stream().findFirst()
                .orElseThrow(() -> new HandoverNotFoundException("Không tìm thấy khoá chặn"));
    }

    /** FS đang hoạt động được gán cơ sở + FM của cơ sở. Lỗi gửi chỉ log — việc vẫn hiện ở trang khoá chặn. */
    private void notifyWork(OverlockView view, NotificationType type) {
        Map<String, Object> values = values(view);
        List<Map<String, Object>> staff = jdbc.queryForList("""
                select a.id, a.email from accounts a
                join roles r on r.id = a.role_id and r.name = 'FS'
                join account_facility_assignments f on f.account_id = a.id
                where f.facility_id = ? and a.status = 'ACTIVE'""", view.facilityId());
        for (Map<String, Object> row : staff) {
            try {
                notificationService.notify(((Number) row.get("id")).longValue(), (String) row.get("email"),
                        NotificationRecipient.FS, type, values, view.orderId());
            } catch (RuntimeException e) {
                LOG.warn("Không gửi được {} cho FS {}: {}", type, row.get("id"), e.getMessage());
            }
        }
        orderNotifications.notifyFacilityManager(view.orderId(), type, values);
    }

    private static Map<String, Object> values(OverlockView view) {
        Map<String, Object> values = new HashMap<>();
        values.put("contractCode", view.contractCode());
        values.put("unitCode", view.unitCode());
        values.put("facilityName", view.facilityName());
        return values;
    }

    private static String trimNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > NOTE_MAX) {
            throw new HandoverValidationException("Ghi chú tối đa " + NOTE_MAX + " ký tự");
        }
        return trimmed;
    }

    private void audit(AuditAction action, UnitOverlock lock, String before, Long actorAccountId) {
        Map<String, Object> after = new HashMap<>();
        after.put("status", lock.getStatus());
        after.put("contractId", lock.getContractId());
        after.put("unitId", lock.getUnitId());
        if (lock.getNote() != null) {
            after.put("note", lock.getNote());
        }
        auditRecorder.record(new AuditEntry(action, AuditEntityType.UNIT_OVERLOCK, String.valueOf(lock.getId()),
                before == null ? null : Map.of("status", before), after, null, actorAccountId));
    }

    private static final String OPEN = "o.status in ('Required', 'Applied', 'RemovalRequired')";

    private static final String SELECT = """
            select o.id, o.contract_id, c.code as contract_code, c.order_id, o.unit_id, u.code as unit_code,
                   u.facility_id, f.name as facility_name, o.status, o.required_at, o.applied_at,
                   o.removal_requested_at, o.removed_at, o.note
            from unit_overlocks o
            join rental_contracts c on c.id = o.contract_id
            join storage_units u on u.id = o.unit_id
            join facilities f on f.id = u.facility_id""";

    private static OverlockView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OverlockView(rs.getLong("id"), rs.getLong("contract_id"), rs.getString("contract_code"),
                rs.getLong("order_id"), rs.getLong("unit_id"), rs.getString("unit_code"), rs.getLong("facility_id"),
                rs.getString("facility_name"), rs.getString("status"), instant(rs.getTimestamp("required_at")),
                instant(rs.getTimestamp("applied_at")), instant(rs.getTimestamp("removal_requested_at")),
                instant(rs.getTimestamp("removed_at")), rs.getString("note"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
