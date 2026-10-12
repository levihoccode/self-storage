package vn.lemar.selfstorage.notification.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.lemar.selfstorage.notification.domain.OrderNotification;
import vn.lemar.selfstorage.notification.domain.OrderNotificationId;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, OrderNotificationId> {

    List<OrderNotification> findByOrderId(Long orderId);

    List<OrderNotification> findByNotificationIdIn(Collection<Long> notificationIds);

    Optional<OrderNotification> findByNotificationId(Long notificationId);
}
