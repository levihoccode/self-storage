package vn.lemar.selfstorage.booking.repository;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;

public interface RentalRequestRepository extends JpaRepository<RentalRequest, Long> {

    Page<RentalRequest> findByFacilityIdAndStatus(Long facilityId, RentalRequestStatus status,
            Pageable pageable);

    Page<RentalRequest> findByFacilityId(Long facilityId, Pageable pageable);

    /**
     * Khoang đã gán cho request khác của cùng email đang hiệu lực (#126) — chặn gán trùng lúc duyệt.
     */
    boolean existsByNormalizedCustomerEmailAndUnitIdAndStatusIn(String normalizedCustomerEmail,
            Long unitId, Collection<RentalRequestStatus> statuses);
}
