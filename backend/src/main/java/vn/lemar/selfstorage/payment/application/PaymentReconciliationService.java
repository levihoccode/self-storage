package vn.lemar.selfstorage.payment.application;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.lemar.selfstorage.payment.application.dto.TransactionQuery;
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;

/**
 * Đối soát các phiên Pending đã quá cửa sổ thanh toán. Kết quả lấy từ API truy vấn của cổng, không
 * lấy từ đồng hồ: một giao dịch có thể thành công dù callback/IPN không về.
 *
 * <p>Dùng chung cho mọi provider: adapter ({@link PaymentGateway#queryTransaction}) đã chuẩn hoá mã
 * riêng của cổng sang {@link TransactionQueryResult.Outcome}. HTTP chạy ngoài transaction; chỉ khi
 * có kết quả mới mở transaction ngắn để khoá row và ghi, tránh giữ khoá trong lúc chờ cổng.
 */
@Service
public class PaymentReconciliationService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final int BATCH_SIZE = 50;
    private static final Duration MAX_PENDING_AGE = Duration.ofMinutes(60);
    private static final String PREFIX = "đối soát: ";

    private final PaymentTransactionRepository repository;
    private final PaymentGateway gateway;
    private final PaymentService payments;

    public PaymentReconciliationService(PaymentTransactionRepository repository,
                                        PaymentGateway gateway, PaymentService payments) {
        this.repository = repository;
        this.gateway = gateway;
        this.payments = payments;
    }

    /** Trả về số giao dịch đã được chốt (Success hoặc Failed). */
    public int reconcilePending() {
        Instant now = Instant.now();
        List<PaymentTransaction> pending = repository
                .findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        PaymentStatus.PENDING, now.minus(PaymentSession.WINDOW),
                        PageRequest.of(0, BATCH_SIZE));

        int applied = 0;
        for (PaymentTransaction transaction : pending) {
            try {
                if (reconcileOne(transaction)) {
                    applied++;
                }
            } catch (RuntimeException e) {
                LOG.warn("Đối soát giao dịch {} lỗi: {}", transaction.getVnpTxnRef(), e.getMessage());
            }
        }
        return applied;
    }

    private boolean reconcileOne(PaymentTransaction transaction) {
        TransactionQueryResult result;
        try {
            result = gateway.queryTransaction(new TransactionQuery(
                    transaction.getVnpTxnRef(), transaction.getTransactionContent(),
                    transaction.getCreatedAt(), serverIp()));
        } catch (RuntimeException e) {
            LOG.warn("Truy vấn {} lỗi cho {}: {}", gateway.provider(), transaction.getVnpTxnRef(), e.getMessage());
            return false;
        }

        switch (result.outcome()) {
            case NOT_FOUND:
                return payments.reconcileFailure(transaction.getId(),
                        PREFIX + "không tìm thấy giao dịch", result.rawResponse());
            case UNKNOWN:
                LOG.warn("Truy vấn {} chưa xác định cho {}: {}", gateway.provider(),
                        transaction.getVnpTxnRef(), result.message());
                return false;
            default:
                break;
        }
        // Cổng có trả số tiền thì phải khớp số tiền đã dùng mở phiên; thành công mà thiếu số tiền
        // cũng không được ghi.
        boolean amountMissing = result.amount() == null;
        if ((amountMissing && result.outcome() == TransactionQueryResult.Outcome.SUCCESS)
                || (!amountMissing && result.amount().compareTo(transaction.getAmount()) != 0)) {
            LOG.error("Đối soát lệch số tiền cho {}: gateway={} local={}",
                    transaction.getVnpTxnRef(), result.amount(), transaction.getAmount());
            return false;
        }

        switch (result.outcome()) {
            case SUCCESS:
                return payments.reconcileSuccess(transaction.getId(), result.transactionNo(),
                        result.paidAt(), result.rawResponse());
            case FAILED:
                return payments.reconcileFailure(transaction.getId(),
                        PREFIX + result.message(), result.rawResponse());
            case PENDING:
                if (transaction.getCreatedAt().isBefore(Instant.now().minus(MAX_PENDING_AGE))) {
                    return payments.reconcileFailure(transaction.getId(),
                            PREFIX + "chưa hoàn tất quá " + MAX_PENDING_AGE.toMinutes() + " phút",
                            result.rawResponse());
                }
                LOG.info("Đối soát chưa chốt được {}: còn đang xử lý", transaction.getVnpTxnRef());
                return false;
            default:
                return false;
        }
    }

    private static String serverIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }
}
