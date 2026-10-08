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
import vn.lemar.selfstorage.identity.domain.RoleName;
import vn.lemar.selfstorage.identity.repository.AccountFacilityAssignmentRepository;
import vn.lemar.selfstorage.identity.repository.PermissionRepository;

/**
 * Điểm kiểm quyền RBAC DUY NHẤT của hệ thống. Mọi guard gọi một trong hai overload; Flow 5 chỉ
 * thay ruột bên trong, không đụng call site.
 *
 * <p>Permission code lấy từ catalog: backend/docs/permission-catalog.md — code dùng trong source
 * phải có trong catalog trước.
 *
 * <ul>
 *   <li>{@code can(permission)} — hành động toàn cục.</li>
 *   <li>{@code can(permission, facilityId)} — hành động theo cơ sở; facilityId bắt buộc.
 *       ADMIN/BOM bỏ qua scope; FM kiểm facilities.fm_account_id; FS kiểm
 *       account_facility_assignments.</li>
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

    /** Hành động toàn cục. Ném ForbiddenException (-> 403) nếu role không có permission. */
    @Transactional(readOnly = true)
    public void can(String permissionCode) {
        Objects.requireNonNull(permissionCode, "permissionCode");
        check(permissionCode, null);
    }

    /**
     * Hành động theo cơ sở: role phải có permission và facilityId phải thuộc phạm vi của account.
     * facilityId bắt buộc — null là lỗi lập trình (NPE), không có nghĩa "bỏ scope".
     */
    @Transactional(readOnly = true)
    public void can(String permissionCode, Long facilityId) {
        Objects.requireNonNull(permissionCode, "permissionCode");
        check(permissionCode, Objects.requireNonNull(facilityId, "facilityId bắt buộc cho hành động theo cơ sở"));
    }

    private void check(String permissionCode, Long facilityId) {
        Account account = currentAccountProvider.getCurrentAccount();

        if (!permissionRepository.roleHasPermission(account.getRole().getId(), permissionCode)) {
            throw new ForbiddenException();
        }

        if (facilityId == null) {
            return;
        }

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
}
