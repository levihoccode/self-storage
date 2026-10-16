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
 * Đơn thuê ({@code specs/db-table-draft.md} › RentalOrder).
 *
 * <p>Đơn do luồng convert (B2) tạo; entity này chỉ mở các thao tác B3 cần. {@code unitId},
 * {@code customerId}, {@code requestId} để kiểu {@code Long} chứ không phải quan hệ JPA: bảng đích
 * thuộc module khác — ranh giới module cấm import {@code domain} của nhau, khoá ngoại vẫn do DB giữ.
 */
@Entity
@Table(name = "rental_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true)
    private Long requestId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Convert(converter = RentalOrderStatusConverter.class)
    @Column(nullable = false, length = 20)
    private RentalOrderStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * FM cần đề xuất lại dù đơn đã qua {@code Pending} (vd khách từ chối khoang tại check-in). Flow 2 bật/tắt cờ;
     * B3 chỉ đọc trong điều kiện đề xuất lại (chốt PR #136).
     */
    @Column(name = "needs_reproposal", nullable = false)
    private boolean needsReproposal;

    /**
     * Chốt PR #136: FM được đề xuất lại khi đơn chưa đóng và đang {@code Pending} hoặc được đánh dấu
     * {@code needs_reproposal}. Điều kiện "không có proposal Pending" do service kiểm (cần truy vấn proposal).
     */
    public boolean allowsReproposal() {
        return !status.isClosed() && (status == RentalOrderStatus.PENDING || needsReproposal);
    }

    /** Spec 1.3: {@code RentalOrder.unit_id} = {@code unit_id} của proposal {@code Agreed} mới nhất. */
    public void assignUnit(Long agreedUnitId) {
        this.unitId = agreedUnitId;
    }

    /** Cancellation Cascade bước 1: {@code status -> Canceled}, ghi {@code cancel_reason}. */
    public void cancel(String reason) {
        if (status.isClosed()) {
            throw new IllegalStateException("Đơn " + id + " đã đóng: " + status);
        }
        this.status = RentalOrderStatus.CANCELED;
        this.cancelReason = reason;
    }
}
