package vn.lemar.selfstorage.handover.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.lemar.selfstorage.handover.domain.UnitOverlock;

public interface UnitOverlockRepository extends JpaRepository<UnitOverlock, Long> {

    /** Khoá chặn đang mở của hợp đồng (tối đa một — partial unique index V38). */
    @Query("select o from UnitOverlock o where o.contractId = :contractId "
            + "and o.status in ('Required', 'Applied', 'RemovalRequired')")
    Optional<UnitOverlock> findOpen(@Param("contractId") Long contractId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from UnitOverlock o where o.contractId = :contractId "
            + "and o.status in ('Required', 'Applied', 'RemovalRequired')")
    Optional<UnitOverlock> findOpenForUpdate(@Param("contractId") Long contractId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from UnitOverlock o where o.id = :id")
    Optional<UnitOverlock> findByIdForUpdate(@Param("id") Long id);
}
