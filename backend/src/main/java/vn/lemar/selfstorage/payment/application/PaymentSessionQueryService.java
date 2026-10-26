package vn.lemar.selfstorage.payment.application;

import java.time.Instant;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.payment.domain.PaymentDirection;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;

/** Đọc trạng thái phiên từ chính bảng giao dịch — không có khóa riêng nào để lệch. */
@Service
public class PaymentSessionQueryService implements PaymentSessionQuery {

    private final PaymentTransactionRepository repository;

    public PaymentSessionQueryService(PaymentTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOpenSession(Collection<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return false;
        }
        return repository.existsByInvoiceIdInAndStatusAndCreatedAtAfter(
                invoiceIds, PaymentStatus.PENDING, Instant.now().minus(PaymentSession.WINDOW));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSuccessfulPayment(Long invoiceId) {
        return repository.existsByInvoiceIdAndDirectionAndStatus(
                invoiceId, PaymentDirection.PAY, PaymentStatus.SUCCESS);
    }
}
