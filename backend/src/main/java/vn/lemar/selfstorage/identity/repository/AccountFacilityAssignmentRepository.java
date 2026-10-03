package vn.lemar.selfstorage.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.lemar.selfstorage.identity.domain.AccountFacilityAssignment;
import vn.lemar.selfstorage.identity.domain.AccountFacilityAssignmentId;

public interface AccountFacilityAssignmentRepository
        extends JpaRepository<AccountFacilityAssignment, AccountFacilityAssignmentId> {

    boolean existsByAccountIdAndFacilityId(Long accountId, Long facilityId);

    /** FM phụ trách cơ sở: facilities.fm_account_id (spec chốt quan hệ 1-1). */
    @Query(value = "select count(*) > 0 from facilities "
            + "where id = :facilityId and fm_account_id = :accountId", nativeQuery = true)
    boolean isFmOfFacility(@Param("accountId") Long accountId,
                           @Param("facilityId") Long facilityId);
}