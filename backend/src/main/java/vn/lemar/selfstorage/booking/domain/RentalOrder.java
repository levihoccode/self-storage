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
     * Spec 1.1/1.2: đơn convert từ request — {@code Pending}, chưa gán {@code unit_id} cho tới khi
     * proposal {@code Agreed}; {@code expires_at} set một lần lúc tạo.
     */
    public static RentalOrder open(Long requestId, Long customerId, Instant expiresAt) {
        RentalOrder order = new RentalOrder();
        order.requestId = requestId;
        order.customerId = customerId;
        order.status = RentalOrderStatus.PENDING;
        order.expiresAt = expiresAt;
        return order;
    }

    /** Spec 1.3: {@code RentalOrder.unit_id} = {@code unit_id} của proposal {@code Agreed} mới nhất. */
    public void assignUnit(Long agreedUnitId) {
        this.unitId = agreedUnitId;
    }
}
