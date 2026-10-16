package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Đơn thuê kho. Flow 1.4 đọc {@code expiresAt} để chặn thanh toán khi đơn đã quá hạn, và đẩy
 * đơn sang {@code Deposited} khi cổng xác nhận thu cọc.
 */
@Entity
@Table(name = "rental_orders")
public class RentalOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "unit_id")
    private Long unitId;

    @Convert(converter = RentalOrderStatusConverter.class)
    @Column(nullable = false, length = 20)
    private RentalOrderStatus status = RentalOrderStatus.PENDING;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected RentalOrder() {
        // JPA
    }

    /** Quá hạn tính theo mốc truyền vào để test không phụ thuộc đồng hồ hệ thống. */
    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    /**
     * Ghi nhận đã thu cọc. {@code expiresAt} chuyển sang hạn giữ kho sau cọc
     * ({@code order.deposit_expiry_days}) — trước đó nó là hạn thanh toán.
     */
    public void markDeposited(Instant holdUntil) {
        this.status = RentalOrderStatus.DEPOSITED;
        this.expiresAt = holdUntil;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public RentalOrderStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
