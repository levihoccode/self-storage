package vn.lemar.selfstorage.payment.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.lemar.selfstorage.payment.domain.RefundTransaction;
import vn.lemar.selfstorage.payment.domain.RefundTransactionStatus;

public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, Long> {

    boolean existsBySourceTransactionIdAndStatus(Long sourceTransactionId,
            RefundTransactionStatus status);

    Optional<RefundTransaction> findByIdAndInvoiceId(Long id, Long invoiceId);

    Optional<RefundTransaction> findFirstByInvoiceIdAndStatusOrderByDisbursedAtDesc(
            Long invoiceId, RefundTransactionStatus status);

    List<RefundTransaction> findByInvoiceIdAndStatusOrderByCreatedAtDesc(
            Long invoiceId, RefundTransactionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundTransaction r where r.id = :id")
    Optional<RefundTransaction> findByIdForUpdate(@Param("id") Long id);
}
