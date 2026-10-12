package vn.lemar.selfstorage.notification.domain;

import java.io.Serializable;
import java.util.Objects;

public class OrderNotificationId implements Serializable {

    private Long orderId;

    private Long notificationId;

    public OrderNotificationId() {
        // JPA
    }

    public OrderNotificationId(Long orderId, Long notificationId) {
        this.orderId = orderId;
        this.notificationId = notificationId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderNotificationId that)) {
            return false;
        }
        return Objects.equals(orderId, that.orderId) && Objects.equals(notificationId, that.notificationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, notificationId);
    }
}
