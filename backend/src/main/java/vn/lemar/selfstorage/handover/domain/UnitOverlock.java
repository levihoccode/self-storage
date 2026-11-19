package vn.lemar.selfstorage.handover.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Khoá chặn của cơ sở (overlock) gắn lên khoang dùng chìa cơ khi hợp đồng quá hạn kéo dài bị tạm khoá truy cập — chìa
 * cơ không thu hồi từ xa được như mã số. Vòng đời (CHECK V38):
 * {@code Required → Applied → RemovalRequired → Removed}; mở lại trước khi FS kịp gắn thì {@code Required → Canceled}.
 * Mỗi hợp đồng tối đa một khoá chặn đang mở (partial unique index V38).
 */
@Entity
@Table(name = "unit_overlocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnitOverlock {

    public static final String REQUIRED = "Required";
    public static final String APPLIED = "Applied";
    public static final String REMOVAL_REQUIRED = "RemovalRequired";
    public static final String REMOVED = "Removed";
    public static final String CANCELED = "Canceled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "required_at", nullable = false)
    private Instant requiredAt;

    @Column(name = "applied_by")
    private Long appliedBy;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "removal_requested_at")
    private Instant removalRequestedAt;

    @Column(name = "removed_by")
    private Long removedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(length = 500)
    private String note;

    /** FS được FM giao gắn / gỡ; {@code null} = chưa giao (mọi FS của cơ sở nhận việc). */
    @Column(name = "assigned_staff_id")
    private Long assignedStaffId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    public static UnitOverlock require(Long contractId, Long unitId, Instant now) {
        UnitOverlock lock = new UnitOverlock();
        lock.contractId = Objects.requireNonNull(contractId, "contractId");
        lock.unitId = Objects.requireNonNull(unitId, "unitId");
        lock.status = REQUIRED;
        lock.requiredAt = now;
        return lock;
    }

    public boolean open() {
        return REQUIRED.equals(status) || APPLIED.equals(status) || REMOVAL_REQUIRED.equals(status);
    }

    /** FM giao (hoặc giao lại) FS đi gắn / gỡ — chỉ khi đang chờ gắn hoặc chờ gỡ. */
    public void assign(Long staffId, Instant now) {
        if (!REQUIRED.equals(status) && !REMOVAL_REQUIRED.equals(status)) {
            throw new IllegalStateException("UnitOverlock " + id + " không có việc để giao: " + status);
        }
        this.assignedStaffId = Objects.requireNonNull(staffId, "staffId");
        this.assignedAt = now;
    }

    /** FS này được làm việc khoá chặn: chưa giao ai, hoặc giao đúng FS này. */
    public boolean actionableBy(Long staffId) {
        return assignedStaffId == null || assignedStaffId.equals(staffId);
    }

    /** FS đã gắn khoá chặn tại khoang: {@code Required → Applied}. */
    public void apply(Long staffId, String note, Instant now) {
        if (!REQUIRED.equals(status)) {
            throw new IllegalStateException("UnitOverlock " + id + " không chờ gắn: " + status);
        }
        this.status = APPLIED;
        this.appliedBy = staffId;
        this.appliedAt = now;
        this.note = note;
    }

    /**
     * Truy cập được mở lại: chưa gắn thì huỷ ({@code Canceled}), đã gắn thì chờ FS gỡ ({@code RemovalRequired}).
     * {@code true} khi cần gỡ tại khoang.
     */
    public boolean release(Instant now) {
        if (REQUIRED.equals(status)) {
            this.status = CANCELED;
            this.canceledAt = now;
            return false;
        }
        if (APPLIED.equals(status)) {
            this.status = REMOVAL_REQUIRED;
            this.removalRequestedAt = now;
            return true;
        }
        return REMOVAL_REQUIRED.equals(status);
    }

    /** FS đã gỡ khoá chặn: {@code RemovalRequired → Removed}. */
    public void remove(Long staffId, String note, Instant now) {
        if (!REMOVAL_REQUIRED.equals(status)) {
            throw new IllegalStateException("UnitOverlock " + id + " không chờ gỡ: " + status);
        }
        this.status = REMOVED;
        this.removedBy = staffId;
        this.removedAt = now;
        if (note != null) {
            this.note = note;
        }
    }
}
