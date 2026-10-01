package vn.lemar.selfstorage.identity.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.identity.application.exception.ForbiddenException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.RoleName;
import vn.lemar.selfstorage.identity.repository.AccountFacilityAssignmentRepository;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Diem kiem quyen DUY NHAT cua he thong (issue #15 muc 7). Moi noi can guard
 * (controller/service) chi goi 2 ham public o day. Flow 5 sau nay chi doi
 * ruot ben trong can()/canAccessFacility(), khong dung toi call site nao.
 *
 * <p>Ca 2 ham deu @Transactional vi Account.role la LAZY fetch va
 * open-in-view=false — phai doc role trong cung 1 session voi luc load
 * account, neu khong se gap LazyInitializationException.
 *
 * <pre>
 *   access.can(RoleName.ADMIN, RoleName.BOM);
 *   access.canAccessFacility(facilityId);
 * </pre>
 */
@Component
public class Access {

    // ADMIN, BOM co quyen toan cuc — khong scope theo facility.
    private static final Set<RoleName> GLOBAL_FACILITY_ROLES = EnumSet.of(RoleName.ADMIN, RoleName.BOM);

    private final CurrentAccountProvider currentAccountProvider;
    private final AccountFacilityAssignmentRepository assignmentRepository;

    public Access(CurrentAccountProvider currentAccountProvider,
                  AccountFacilityAssignmentRepository assignmentRepository) {
        this.currentAccountProvider = currentAccountProvider;
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Chan neu account hien tai khong co role nam trong allowedRoles.
     * Throw ForbiddenException (-> 403) neu khong hop le.
     */
    @Transactional(readOnly = true)
    public void can(RoleName... allowedRoles) {
        Account account = currentAccountProvider.getCurrentAccount();
        String currentRoleName = account.getRole().getName();

        boolean allowed = Arrays.stream(allowedRoles)
                .anyMatch(role -> role.name().equals(currentRoleName));

        if (!allowed) {
            throw new ForbiddenException(
                    "Role " + currentRoleName + " khong duoc phep thuc hien thao tac nay");
        }
    }

    /**
     * Chan neu account hien tai khong duoc gan (AccountFacilityAssignment)
     * vao facilityId nay. ADMIN/BOM luon duoc phep (khong can assignment).
     * Throw ForbiddenException (-> 403) neu khong hop le.
     */
    @Transactional(readOnly = true)
    public void canAccessFacility(Long facilityId) {
        Account account = currentAccountProvider.getCurrentAccount();
        String currentRoleName = account.getRole().getName();

        boolean isGlobalRole = GLOBAL_FACILITY_ROLES.stream()
                .anyMatch(role -> role.name().equals(currentRoleName));
        if (isGlobalRole) {
            return;
        }

        boolean assigned = assignmentRepository.existsByAccountIdAndFacilityId(
                account.getId(), facilityId);

        if (!assigned) {
            throw new ForbiddenException(
                    "Account khong duoc gan vao facility id=" + facilityId);
        }
    }
}