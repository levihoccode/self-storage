package vn.lemar.selfstorage.payment.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByVnpTxnRef(String vnpTxnRef);

    boolean existsByVnpTxnRef(String vnpTxnRef);
}
