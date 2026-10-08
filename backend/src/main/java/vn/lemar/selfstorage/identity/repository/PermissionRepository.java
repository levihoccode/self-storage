package vn.lemar.selfstorage.identity.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.lemar.selfstorage.identity.domain.PermissionScope;

/** Đọc dữ liệu RBAC data-driven: role → permission (catalog: backend/docs/permission-catalog.md). */
@Repository
public class PermissionRepository {

    private final JdbcTemplate jdbc;

    public PermissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Scope của mapping role → permission; empty nếu role không có permission. */
    public Optional<PermissionScope> findScope(Long roleId, String permissionCode) {
        List<String> scopes = jdbc.queryForList(
                "select rp.scope from role_permissions rp"
                        + " join permissions p on p.id = rp.permission_id"
                        + " where rp.role_id = ? and p.code = ?",
                String.class, roleId, permissionCode);
        return scopes.stream().findFirst().map(PermissionScope::valueOf);
    }
}
