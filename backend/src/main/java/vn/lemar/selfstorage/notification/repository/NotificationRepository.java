package vn.lemar.selfstorage.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.lemar.selfstorage.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    long countByAccountIdAndReadAtIsNull(Long accountId);
}