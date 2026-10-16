package vn.lemar.selfstorage.booking.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.InvoiceStatus;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByOrderIdAndStatus(Long orderId, InvoiceStatus status);
}
