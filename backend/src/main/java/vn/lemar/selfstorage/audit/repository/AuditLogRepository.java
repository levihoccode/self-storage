package vn.lemar.selfstorage.audit.repository;

import java.util.List;
import org.springframework.data.repository.Repository;
import vn.lemar.selfstorage.audit.domain.AuditLog;

/**
 * Append-only: interface chỉ khai đường ghi thêm ({@code save}) và đọc — không expose
 * update/delete dù tầng JPA bên dưới có thể làm được. Bảng {@code audit_logs} không có đường
 * sửa/xóa trong code.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    /** Ghi thêm một bản ghi mới. */
    AuditLog save(AuditLog log);

    /** Đọc toàn bộ — test và tra cứu; không có đường sửa/xóa. */
    List<AuditLog> findAll();
}
