package vn.lemar.selfstorage.checkout.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.lemar.selfstorage.checkout.domain.ReclaimedGoods;

public interface ReclaimedGoodsRepository extends JpaRepository<ReclaimedGoods, Long> {

    Optional<ReclaimedGoods> findByContractId(Long contractId);

    List<ReclaimedGoods> findByFacilityIdOrderByIdDesc(Long facilityId);

    List<ReclaimedGoods> findByStatusOrderByIdAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ReclaimedGoods g where g.id = :id")
    Optional<ReclaimedGoods> findByIdForUpdate(@Param("id") Long id);
}
