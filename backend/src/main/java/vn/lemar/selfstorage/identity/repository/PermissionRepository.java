package vn.lemar.selfstorage.identity.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc dữ liệu RBAC data-driven: role → permission (catalog: backend/docs/permission-catalog.md). */
@Repository
public class PermissionRepository {

    private final JdbcTemplate jdbc;

    public PermissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean roleHasPermission(Long roleId, String permissionCode) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from role_permissions rp"
                        + " join permissions p on p.id = rp.permission_id"
                        + " where rp.role_id = ? and p.code = ?)",
                Boolean.class, roleId, permissionCode));
    }
}
