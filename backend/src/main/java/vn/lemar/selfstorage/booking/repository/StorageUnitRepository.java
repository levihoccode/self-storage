package vn.lemar.selfstorage.booking.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.lemar.selfstorage.booking.domain.StorageUnit;

public interface StorageUnitRepository extends JpaRepository<StorageUnit, Long> {

    /**
     * Khoá row khoang ({@code SELECT ... FOR UPDATE}) trước khi quyết cho mở phiên thanh toán.
     *
     * <p>Khóa phiên là phép <em>đọc rồi mới ghi</em>: kiểm "khoang có giao dịch nào đang mở không"
     * xong mới tạo {@code PaymentTransaction}. Hai request song song trên cùng khoang đều đọc thấy
     * "rảnh" rồi cùng mở phiên — nhánh 4 của spec coi như không tồn tại. Khoá ở đây buộc chúng xếp
     * hàng: luồng sau chỉ đọc được sau khi luồng trước đã commit bản ghi Pending.
     *
     * <p>Phải gọi trong transaction. {@code DepositConcurrencyIntegrationTest} đỏ nếu bỏ khoá này.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from StorageUnit u where u.id = :id")
    Optional<StorageUnit> findByIdForUpdate(@Param("id") Long id);
}
