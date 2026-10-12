package vn.lemar.selfstorage.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.lemar.selfstorage.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    Page<Notification> findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    Page<Notification> findByAccountIdAndReadAtIsNotNullOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    Optional<Notification> findByIdAndAccountId(Long id, Long accountId);

    List<Notification> findByAccountIdAndReadAtIsNull(Long accountId);

    long countByAccountIdAndReadAtIsNull(Long accountId);
}
