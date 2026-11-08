package vn.lemar.selfstorage.payment.repository;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.payment.domain.RefundRequest;
import vn.lemar.selfstorage.payment.domain.RefundRequestStatus;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    boolean existsByInvoiceIdAndStatusIn(Long invoiceId, Collection<RefundRequestStatus> statuses);

    Optional<RefundRequest> findByIdAndInvoiceId(Long id, Long invoiceId);
}
