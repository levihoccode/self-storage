package vn.lemar.selfstorage.booking.repository;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.InvoiceStatus;
import vn.lemar.selfstorage.booking.domain.InvoiceType;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByOrderIdAndStatus(Long orderId, InvoiceStatus status);

    /** Tổng tiền đã thu của một loại hoá đơn trên đơn — dùng để tính chênh lệch cọc. */
    @Query("select coalesce(sum(i.amount), 0) from Invoice i where i.orderId = :orderId "
            + "and i.type = :type and i.status = :status")
    BigDecimal sumAmount(@Param("orderId") Long orderId, @Param("type") InvoiceType type,
                         @Param("status") InvoiceStatus status);
}
