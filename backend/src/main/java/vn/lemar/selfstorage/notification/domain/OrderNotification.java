package vn.lemar.selfstorage.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Liên kết thông báo dành cho một đơn hàng; mỗi {@link Notification} tối đa một liên kết.
 *
 * <p>Không map {@code @ManyToOne} sang booking — tránh phụ thuộc module chéo; FK do DB giữ.
 */
@Entity
@Table(name = "order_notifications")
@IdClass(OrderNotificationId.class)
public class OrderNotification {

    @Id
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Id
    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    protected OrderNotification() {
        // JPA
    }

    public OrderNotification(Long orderId, Long notificationId) {
        this.orderId = orderId;
        this.notificationId = notificationId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getNotificationId() {
        return notificationId;
    }
}
