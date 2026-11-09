package vn.lemar.selfstorage.payment.repository;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.payment.domain.RefundObligation;
import vn.lemar.selfstorage.payment.domain.RefundObligationStatus;

public interface RefundObligationRepository extends JpaRepository<RefundObligation, Long> {

    boolean existsByInvoiceIdAndStatusIn(Long invoiceId, Collection<RefundObligationStatus> statuses);

    Optional<RefundObligation> findByIdAndInvoiceId(Long id, Long invoiceId);
}
