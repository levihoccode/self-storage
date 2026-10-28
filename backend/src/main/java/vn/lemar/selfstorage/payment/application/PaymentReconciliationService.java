package vn.lemar.selfstorage.payment.application;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.lemar.selfstorage.payment.application.dto.QueryDrRequest;
import vn.lemar.selfstorage.payment.application.dto.QueryDrResult;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;

/**
 * Đối soát các phiên Pending đã quá cửa sổ thanh toán. Kết quả lấy từ {@code querydr}, không lấy
 * từ đồng hồ: một giao dịch có thể thành công dù IPN không về.
 *
 * <p>HTTP querydr chạy ngoài transaction; chỉ khi có kết quả mới mở transaction ngắn để khoá row
 * và ghi, tránh giữ khoá trong lúc chờ cổng.
 */
@Service
public class PaymentReconciliationService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final String SUCCESS_CODE = "00";
    private static final String NOT_FOUND_CODE = "91";
    private static final int BATCH_SIZE = 50;
    private static final Duration MAX_PENDING_AGE = Duration.ofMinutes(60);
    private static final Set<String> FAILED_STATUSES = Set.of("02", "04", "07", "09");
    private static final Set<String> STILL_PENDING_STATUSES = Set.of("01", "05", "06");

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
        QueryDrResult result;
        try {
            result = gateway.queryDr(new QueryDrRequest(
                    transaction.getVnpTxnRef(), transaction.getTransactionContent(),
                    transaction.getCreatedAt(), serverIp()));
        } catch (RuntimeException e) {
            LOG.warn("querydr lỗi cho {}: {}", transaction.getVnpTxnRef(), e.getMessage());
            return false;
        }

        if (NOT_FOUND_CODE.equals(result.responseCode())) {
            return payments.reconcileFailure(transaction.getId(),
                    "querydr: không tìm thấy giao dịch", result.rawResponse());
        }
        if (!SUCCESS_CODE.equals(result.responseCode())) {
            LOG.warn("querydr trả ResponseCode={} cho {}: {}",
                    result.responseCode(), transaction.getVnpTxnRef(), result.message());
            return false;
        }
        if (result.amount() == null || result.amount().compareTo(transaction.getAmount()) != 0) {
            LOG.error("querydr lệch số tiền cho {}: gateway={} local={}",
                    transaction.getVnpTxnRef(), result.amount(), transaction.getAmount());
            return false;
        }
        if (SUCCESS_CODE.equals(result.transactionStatus())) {
            return payments.reconcileSuccess(transaction.getId(), result.transactionNo(),
                    result.paidAt(), result.rawResponse());
        }
        if (FAILED_STATUSES.contains(result.transactionStatus())) {
            return payments.reconcileFailure(transaction.getId(),
                    "querydr: " + result.message(), result.rawResponse());
        }
        if (STILL_PENDING_STATUSES.contains(result.transactionStatus())
                && transaction.getCreatedAt().isBefore(Instant.now().minus(MAX_PENDING_AGE))) {
            return payments.reconcileFailure(transaction.getId(),
                    "querydr: chưa hoàn tất quá " + MAX_PENDING_AGE.toMinutes() + " phút",
                    result.rawResponse());
        }

        LOG.info("querydr chưa chốt được {}: status={}", transaction.getVnpTxnRef(),
                result.transactionStatus());
        return false;
    }

    private static String serverIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }
}
