package vn.lemar.selfstorage.booking.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.InvoiceStatus;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByOrderIdAndStatus(Long orderId, InvoiceStatus status);

    /** Hóa đơn của một nhóm đơn — B4 dùng để hỏi payment xem khoang có phiên nào đang mở. */
    List<Invoice> findByOrderIdIn(List<Long> orderIds);
}
