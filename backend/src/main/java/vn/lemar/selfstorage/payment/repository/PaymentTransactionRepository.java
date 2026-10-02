package vn.lemar.selfstorage.payment.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    /**
     * Khoá row khi xử lý IPN ({@code SELECT ... FOR UPDATE}).
     *
     * <p>VNPay retry tới 10 lần và có thể gọi song song. Nếu chỉ đọc rồi kiểm trạng thái thì hai
     * IPN cùng đọc được {@code Pending} và cùng ghi {@code Success} — ghi nhận trùng tiền. Unique
     * constraint {@code vnp_txn_ref} không chặn được vì cả hai update cùng một row, không sinh row
     * thứ hai. Khoá ở đây buộc IPN thứ hai đợi, đọc lại thấy {@code Success} và trả về {@code 02}.
     *
     * <p>Phải gọi trong transaction. Hiện chỉ có luồng IPN dùng; thêm chỗ đọc thuần sau này thì
     * tách method riêng không khoá.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findByVnpTxnRef(String vnpTxnRef);
}
