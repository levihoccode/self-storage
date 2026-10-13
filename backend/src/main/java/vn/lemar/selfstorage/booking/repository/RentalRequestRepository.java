package vn.lemar.selfstorage.booking.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.lemar.selfstorage.booking.domain.RentalRequest;

public interface RentalRequestRepository extends JpaRepository<RentalRequest, Long> {

    @Query("select r.normalizedCustomerEmail from RentalRequest r where r.id = :id")
    Optional<String> findEmailById(@Param("id") Long id);

    /** Khoá row request trước khi convert — phải gọi trong transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RentalRequest r where r.id = :id")
    Optional<RentalRequest> findByIdForUpdate(@Param("id") Long id);

    /**
     * Request {@code Approved} còn hạn khớp email của account (spec 1.2 Case A), khoá row, thứ tự cũ
     * trước — request duyệt trước được convert trước khi trùng khoang. Phải gọi trong transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RentalRequest r where r.normalizedCustomerEmail = :email "
            + "and r.status = vn.lemar.selfstorage.booking.domain.RentalRequestStatus.APPROVED "
            + "and r.expiresAt > :now order by r.id")
    List<RentalRequest> findClaimableForUpdate(@Param("email") String normalizedEmail, @Param("now") Instant now);

    /**
     * Cron: request {@code Pending}/{@code Approved} đã quá {@code expires_at} → {@code Expired}
     * ({@code draft.md} › Scheduled Jobs). Conditional update nên chạy lặp/đồng thời vẫn an toàn:
     * row đang bị claim khoá thì chờ, sau đó điều kiện status được đánh giá lại.
     */
    @Modifying
    @Query(value = "update rental_requests set status = 'Expired' "
            + "where status in ('Pending', 'Approved') and expires_at < :now", nativeQuery = true)
    int expireOverdue(@Param("now") Instant now);
}
