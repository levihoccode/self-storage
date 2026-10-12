package vn.lemar.selfstorage.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.audit.domain.AuditLog;

/**
 * Append-only: code nghiệp vụ chỉ dùng {@code save} và các truy vấn đọc; bảng không có đường
 * update/delete.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
