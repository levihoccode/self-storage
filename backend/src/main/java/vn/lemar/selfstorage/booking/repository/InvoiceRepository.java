package vn.lemar.selfstorage.booking.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.booking.domain.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Mọi hóa đơn thuộc các đơn đang trỏ vào cùng một khoang — dùng để hỏi payment xem khoang đó
     * có phiên thanh toán nào đang mở không. Khóa là theo khoang chứ không theo hóa đơn: hai khách
     * khác nhau trả cho hai hóa đơn khác nhau vẫn tranh cùng một khoang.
     */
    List<Invoice> findByOrderIdIn(List<Long> orderIds);
}
