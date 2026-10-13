package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu thuê từ form ({@code specs/db-table-draft.md} › RentalRequest).
 *
 * <p>Bản này chỉ map các cột luồng convert (B2) cần; tạo/duyệt/từ chối request thuộc B1 và sẽ bổ
 * sung cột + thao tác khi merge. {@code unitId}, {@code facilityId} để kiểu {@code Long}: bảng đích
 * thuộc module facility.
 */
@Entity
@Table(name = "rental_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "normalized_customer_email", nullable = false)
    private String normalizedCustomerEmail;

    /** Khoang FM chỉ định lúc duyệt — dùng tạo {@code ProposalFeedback} đầu tiên. */
    @Column(name = "unit_id")
    private Long unitId;

    @Convert(converter = RentalRequestStatusConverter.class)
    @Column(nullable = false, length = 20)
    private RentalRequestStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Spec RentalRequest › CONSTRAINTS: chỉ request {@code Approved} chưa quá {@code expires_at} mới
     * được chuyển sang {@code Converted}.
     */
    public boolean isConvertibleAt(Instant now) {
        return status == RentalRequestStatus.APPROVED && expiresAt != null && expiresAt.isAfter(now);
    }

    /** {@code Approved → Converted} khi đã sinh {@code RentalOrder}; caller kiểm {@link #isConvertibleAt}. */
    public void markConverted() {
        if (status != RentalRequestStatus.APPROVED) {
            throw new IllegalStateException("RentalRequest " + id + " không ở Approved: " + status);
        }
        if (unitId == null) {
            throw new IllegalStateException("RentalRequest " + id + " Approved nhưng chưa có unit_id");
        }
        this.status = RentalRequestStatus.CONVERTED;
    }
}
