package vn.lemar.selfstorage.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.identity.domain.AccountFacilityAssignment;
import vn.lemar.selfstorage.identity.domain.AccountFacilityAssignmentId;

public interface AccountFacilityAssignmentRepository
        extends JpaRepository<AccountFacilityAssignment, AccountFacilityAssignmentId> {

    boolean existsByAccountIdAndFacilityId(Long accountId, Long facilityId);
}