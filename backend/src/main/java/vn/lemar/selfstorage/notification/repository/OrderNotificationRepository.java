package vn.lemar.selfstorage.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.lemar.selfstorage.notification.domain.OrderNotification;
import vn.lemar.selfstorage.notification.domain.OrderNotificationId;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, OrderNotificationId> {

    List<OrderNotification> findByOrderId(Long orderId);
}
