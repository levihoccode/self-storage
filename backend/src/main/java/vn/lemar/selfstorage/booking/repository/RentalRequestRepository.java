package vn.lemar.selfstorage.booking.repository;

import java.time.Instant;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.domain.RentalRequestRejectReason;
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

    /**
     * Conditional update chống race duyệt (spec draft.md:135): khoá row, tx thứ 2 re-evaluate
     * {@code WHERE status = 'Pending'} và nhận 0 row. Trả về số row cập nhật — 0 nghĩa là request
     * đã được xử lý bởi FM khác.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RentalRequest r set r.status = :newStatus, r.unitId = :unitId, "
            + "r.respondedAt = :respondedAt, r.expiresAt = :expiresAt "
            + "where r.id = :id and r.status = :expectedStatus")
    int approveIfPending(@Param("id") Long id, @Param("unitId") Long unitId,
            @Param("respondedAt") Instant respondedAt, @Param("expiresAt") Instant expiresAt,
            @Param("expectedStatus") RentalRequestStatus expectedStatus,
            @Param("newStatus") RentalRequestStatus newStatus);

    /** Conditional update chống race từ chối — cùng cơ chế {@link #approveIfPending}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RentalRequest r set r.status = :newStatus, r.rejectReason = :rejectReason, "
            + "r.rejectNote = :rejectNote, r.respondedAt = :respondedAt "
            + "where r.id = :id and r.status = :expectedStatus")
    int rejectIfPending(@Param("id") Long id,
            @Param("rejectReason") RentalRequestRejectReason rejectReason,
            @Param("rejectNote") String rejectNote, @Param("respondedAt") Instant respondedAt,
            @Param("expectedStatus") RentalRequestStatus expectedStatus,
            @Param("newStatus") RentalRequestStatus newStatus);
}
