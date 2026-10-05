package vn.lemar.selfstorage.identity.application;

import java.util.Arrays;
import java.util.EnumSet;
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

/**
 * Điểm kiểm quyền DUY NHẤT của hệ thống (issue #15 mục 7). Mọi nơi cần guard
 * (controller/service) chỉ gọi 2 hàm public ở đây. Flow 5 sau này chỉ đổi
 * ruột bên trong can()/canAccessFacility(), không đụng tới call site nào.
 *
 * <p>Cả 2 hàm đều @Transactional vì Account.role là LAZY fetch và
 * open-in-view=false: phải đọc role trong cùng 1 session với lúc load
 * account, nếu không sẽ gặp LazyInitializationException.
 *
 * <p>Phạm vi cơ sở theo spec: ADMIN/BOM toàn cục; FM theo
 * facilities.fm_account_id; FS theo account_facility_assignments; các role
 * khác bị chặn. Caller vẫn nên gọi can() trước để kiểm role.
 *
 * <pre>
 *   access.can(RoleName.ADMIN, RoleName.BOM);
 *   access.canAccessFacility(facilityId);
 * </pre>
 */
@Component
public class Access {

    private static final Logger LOG = LoggerFactory.getLogger(Access.class);

    // ADMIN, BOM có quyền toàn cục, không bị scope theo cơ sở.
    private static final Set<RoleName> GLOBAL_FACILITY_ROLES =
            EnumSet.of(RoleName.ADMIN, RoleName.BOM);

    private final CurrentAccountProvider currentAccountProvider;
    private final AccountFacilityAssignmentRepository assignmentRepository;
    private final FacilityAccess facilityAccess;

    public Access(CurrentAccountProvider currentAccountProvider,
                  AccountFacilityAssignmentRepository assignmentRepository,
                  FacilityAccess facilityAccess) {
        this.currentAccountProvider = currentAccountProvider;
        this.assignmentRepository = assignmentRepository;
        this.facilityAccess = facilityAccess;
    }

    /**
     * Chặn nếu account hiện tại không có role nằm trong allowedRoles.
     * Ném ForbiddenException (-> 403) nếu không hợp lệ.
     */
    @Transactional(readOnly = true)
    public void can(RoleName... allowedRoles) {
        Account account = currentAccountProvider.getCurrentAccount();
        String currentRoleName = account.getRole().getName();

        boolean allowed = Arrays.stream(allowedRoles)
                .anyMatch(role -> role.name().equals(currentRoleName));

        if (!allowed) {
            throw new ForbiddenException();
        }
    }

    /**
     * Chặn nếu account hiện tại không có quyền với facilityId này.
     * ADMIN/BOM luôn được phép; FM kiểm qua facilities.fm_account_id;
     * FS kiểm qua account_facility_assignments.
     * Ném ForbiddenException (-> 403) nếu không hợp lệ.
     */
    @Transactional(readOnly = true)
    public void canAccessFacility(Long facilityId) {
        if (facilityId == null) {
            throw new ForbiddenException("Không xác định được cơ sở cần truy cập");
        }

        Account account = currentAccountProvider.getCurrentAccount();
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
            LOG.warn("Account {} bị chặn truy cập facility {}", account.getId(), facilityId);
            throw new ForbiddenException("Bạn không được gán vào cơ sở này");
        }
    }
}
