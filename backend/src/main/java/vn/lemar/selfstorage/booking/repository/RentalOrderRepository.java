package vn.lemar.selfstorage.booking.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.booking.domain.RentalOrder;

public interface RentalOrderRepository extends JpaRepository<RentalOrder, Long> {

    /** Các đơn đang trỏ vào cùng một khoang — nền để suy ra khóa giao dịch theo khoang. */
    List<RentalOrder> findByUnitId(Long unitId);
}
