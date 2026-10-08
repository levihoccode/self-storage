package vn.lemar.selfstorage.identity.application;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.facility.application.FacilityAccess;
import vn.lemar.selfstorage.identity.application.exception.ForbiddenException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.PermissionScope;
import vn.lemar.selfstorage.identity.domain.RoleName;
import vn.lemar.selfstorage.identity.repository.AccountFacilityAssignmentRepository;
import vn.lemar.selfstorage.identity.repository.PermissionRepository;

/**
 * Điểm kiểm quyền RBAC DUY NHẤT của hệ thống. Mọi guard gọi một trong hai overload; Flow 5 chỉ
 * thay ruột bên trong, không đụng call site.
 *
 * <p>Permission code + scope lấy từ dữ liệu `role_permissions` (catalog:
 * backend/docs/permission-catalog.md). Dạng gọi phải khớp scope của mapping — gọi sai dạng là lỗi
 * lập trình (IllegalStateException), không âm thầm bỏ scope:
 *
 * <ul>
 *   <li>{@code can(permission)} — chỉ dùng cho mapping {@code GLOBAL}.</li>
 *   <li>{@code can(permission, facilityId)} — chỉ dùng cho mapping {@code FACILITY};
 *       facilityId bắt buộc. ADMIN/BOM bỏ qua scope; FM kiểm facilities.fm_account_id;
 *       FS kiểm account_facility_assignments.</li>
 * </ul>
 *
 * <p>Cả hai hàm đều @Transactional vì Account.role là LAZY fetch và open-in-view=false: phải đọc
 * role + permission trong cùng 1 session với lúc load account, nếu không sẽ gặp
 * LazyInitializationException.
 */
@Component
public class Access {

    private static final Logger LOG = LoggerFactory.getLogger(Access.class);

    // ADMIN, BOM có quyền toàn cục, không bị scope theo cơ sở.
    private static final Set<RoleName> GLOBAL_FACILITY_ROLES =
            EnumSet.of(RoleName.ADMIN, RoleName.BOM);

    private final CurrentAccountProvider currentAccountProvider;
    private final AccountFacilityAssignmentRepository assignmentRepository;
    private final PermissionRepository permissionRepository;
    private final FacilityAccess facilityAccess;

    public Access(CurrentAccountProvider currentAccountProvider,
                  AccountFacilityAssignmentRepository assignmentRepository,
                  PermissionRepository permissionRepository,
                  FacilityAccess facilityAccess) {
        this.currentAccountProvider = currentAccountProvider;
        this.assignmentRepository = assignmentRepository;
        this.permissionRepository = permissionRepository;
        this.facilityAccess = facilityAccess;
    }

    /** Hành động toàn cục. 403 nếu role không có permission; lỗi lập trình nếu mapping là FACILITY. */
    @Transactional(readOnly = true)
    public void can(String permissionCode) {
        requirePermission(permissionCode, PermissionScope.GLOBAL);
    }

    /**
     * Hành động theo cơ sở: role phải có permission scope FACILITY và facilityId phải thuộc phạm
     * vi của account. 403 nếu role không có permission; lỗi lập trình nếu mapping là GLOBAL hoặc
     * facilityId null.
     */
    @Transactional(readOnly = true)
    public void can(String permissionCode, Long facilityId) {
        Objects.requireNonNull(facilityId, "facilityId bắt buộc cho hành động theo cơ sở");
        Account account = requirePermission(permissionCode, PermissionScope.FACILITY);

        RoleName role = RoleName.valueOf(account.getRole().getName());
        if (GLOBAL_FACILITY_ROLES.contains(role)) {
            return;
        }

        boolean allowed = switch (role) {
            case FM -> facilityAccess.isManagedBy(account.getId(), facilityId);
            case FS -> assignmentRepository.existsByAccountIdAndFacilityId(
                    account.getId(), facilityId);
            default -> false;
        };

        if (!allowed) {
            LOG.warn("Account {} bị chặn truy cập facility {} (permission {})",
                    account.getId(), facilityId, permissionCode);
            throw new ForbiddenException("Bạn không được gán vào cơ sở này");
        }
    }

    private Account requirePermission(String permissionCode, PermissionScope requiredScope) {
        Objects.requireNonNull(permissionCode, "permissionCode");
        Account account = currentAccountProvider.getCurrentAccount();

        PermissionScope scope = permissionRepository
                .findScope(account.getRole().getId(), permissionCode)
                .orElseThrow(ForbiddenException::new);

        if (scope != requiredScope) {
            throw new IllegalStateException("Permission " + permissionCode + " có scope " + scope
                    + " — gọi sai dạng can(); xem backend/docs/permission-catalog.md");
        }
        return account;
    }
}
