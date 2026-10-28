package vn.lemar.selfstorage.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.payment.application.dto.IpnResponse;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.application.event.PaymentFailed;
import vn.lemar.selfstorage.payment.application.event.PaymentSucceeded;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;
import vn.lemar.selfstorage.payment.config.VnPayProperties;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;

/**
 * Điểm vào duy nhất để thu tiền. Flow 1 thu cọc, Flow 2 thu tháng đầu, Flow 3 thu gia hạn đều
 * gọi vào đây thay vì tự viết phần VNPay.
 */
@Service
public class PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);

    /** VNPay gửi số tiền đã nhân 100 nên nhận về phải chia lại. */
    private static final BigDecimal AMOUNT_SCALE = BigDecimal.valueOf(100);

    private static final String SUCCESS_CODE = "00";
    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter PAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentGateway gateway;
    private final PaymentTransactionRepository repository;
    private final VnPayProperties properties;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final AuditRecorder audit;
    private final PaymentUrlCache urlCache;

    public PaymentService(PaymentGateway gateway, PaymentTransactionRepository repository,
                          VnPayProperties properties, ApplicationEventPublisher events,
                          ObjectMapper objectMapper, AuditRecorder audit, PaymentUrlCache urlCache) {
        this.gateway = gateway;
        this.repository = repository;
        this.properties = properties;
        this.events = events;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.urlCache = urlCache;
    }

    /**
     * Ghi nhận một lượt thanh toán ở trạng thái {@code Pending} rồi trả URL để redirect khách.
     *
     * <p>Phải tạo bản ghi <em>trước</em> khi chuyển hướng: nếu khách đóng trình duyệt giữa chừng,
     * giao dịch vẫn còn dấu vết để đối soát.
     */
    @Transactional
    public String startPayment(Long invoiceId, PaymentRequest request) {
        Objects.requireNonNull(invoiceId, "invoiceId bắt buộc");
        PaymentTransaction transaction = new PaymentTransaction(
                invoiceId, request.txnRef(), request.amount(), request.orderInfo());
        repository.save(transaction);
        // Dùng cùng mốc createdAt cho URL và transaction: querydr phải gửi đúng
        // vnp_TransactionDate = vnp_CreateDate của URL pay.
        String paymentUrl = gateway.buildPaymentUrl(request, transaction.getCreatedAt());
        urlCache.put(invoiceId, paymentUrl);
        return paymentUrl;
    }

    /**
     * Dựng lại URL của phiên Pending còn hạn khi cache URL bị mất. Không tạo giao dịch mới:
     * cùng {@code vnp_TxnRef} và cùng {@code createdAt}, nên hạn phiên không bị kéo dài.
     *
     * @return URL nếu còn phiên trong 15 phút; rỗng nếu phiên đã hết hạn/không còn
     */
    @Transactional(readOnly = true)
    public Optional<String> resumePayment(Long invoiceId, String clientIp) {
        Instant now = Instant.now();
        Optional<PaymentTransaction> pending = repository
                .findFirstByInvoiceIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        invoiceId, PaymentStatus.PENDING, now.minus(PaymentSession.WINDOW));
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        PaymentTransaction transaction = pending.get();
        Duration remaining = Duration.between(now, transaction.getCreatedAt().plus(PaymentSession.WINDOW));
        if (remaining.isNegative() || remaining.isZero()) {
            return Optional.empty();
        }

        PaymentRequest request = new PaymentRequest(transaction.getVnpTxnRef(), transaction.getAmount(),
                transaction.getTransactionContent(), clientIp);
        String paymentUrl = gateway.buildPaymentUrl(request, transaction.getCreatedAt());
        urlCache.put(invoiceId, paymentUrl, remaining);
        return Optional.of(paymentUrl);
    }

    /**
     * Xử lý IPN — nguồn xác nhận thanh toán duy nhất. Return URL chỉ để hiển thị cho khách,
     * không được dùng để cập nhật dữ liệu.
     */
    @Transactional
    public IpnResponse handleIpn(Map<String, String> params) {
        if (!gateway.verifySignature(params)) {
            LOG.warn("IPN bị từ chối vì sai chữ ký, vnp_TxnRef={}", params.get("vnp_TxnRef"));
            return IpnResponse.invalidSignature();
        }

        String txnRef = params.get("vnp_TxnRef");
        PaymentTransaction transaction = repository.findByVnpTxnRef(txnRef).orElse(null);
        if (transaction == null) {
            return IpnResponse.orderNotFound();
        }

        // Chữ ký đúng nhưng khác merchant nghĩa là giao dịch không thuộc hệ thống này.
        if (!properties.tmnCode().equals(params.get("vnp_TmnCode"))) {
            LOG.warn("IPN mang vnp_TmnCode lạ: {}", params.get("vnp_TmnCode"));
            return IpnResponse.orderNotFound();
        }

        // Số tiền neo vào transaction.amount do caller truyền lúc startPayment. Caller phải đọc từ
        // Invoice trong DB — luồng đặt cọc làm vậy; payment không import booking nên không tự đọc
        // lại. Đối chiếu vnp_Amount với đúng con số đã dùng để dựng URL thanh toán.
        if (!amountMatches(params.get("vnp_Amount"), transaction.getAmount())) {
            return IpnResponse.invalidAmount();
        }

        // Chốt chặn idempotency: VNPay retry tới 10 lần, lần thứ hai trở đi không được ghi lại.
        if (!transaction.isPending()) {
            return IpnResponse.alreadyConfirmed();
        }

        String responsePayload = toJson(params);
        // Hai event dưới đây publish TRƯỚC khi transaction này commit — listener phải đăng ký
        // @TransactionalEventListener(AFTER_COMMIT); xem javadoc PaymentSucceeded/PaymentFailed.
        if (gatewayReportedSuccess(params)) {
            applySuccess(transaction, params.get("vnp_TransactionNo"),
                    parsePayDate(params.get("vnp_PayDate")), responsePayload);
        } else {
            String reason = "vnp_ResponseCode=" + params.get("vnp_ResponseCode")
                    + ", vnp_TransactionStatus=" + params.get("vnp_TransactionStatus");
            applyFailure(transaction, reason, responsePayload);
        }
        return IpnResponse.success();
    }

    /**
     * Job đối soát ghi nhận giao dịch thành công. Khoá row và kiểm lại Pending trong transaction
     * mới để không đè lên IPN vừa xử lý xong.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reconcileSuccess(Long transactionId, String gatewayTransactionNo,
                                    Instant paidAt, String responsePayload) {
        PaymentTransaction transaction = repository.findByIdForUpdate(transactionId).orElseThrow();
        if (!transaction.isPending()) {
            return false;
        }
        applySuccess(transaction, gatewayTransactionNo, paidAt, responsePayload);
        return true;
    }

    /** Job đối soát đóng giao dịch thất bại/không tìm thấy; cùng quy tắc khoá như success. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reconcileFailure(Long transactionId, String reason, String responsePayload) {
        PaymentTransaction transaction = repository.findByIdForUpdate(transactionId).orElseThrow();
        if (!transaction.isPending()) {
            return false;
        }
        applyFailure(transaction, reason, responsePayload);
        return true;
    }

    private void applySuccess(PaymentTransaction transaction, String gatewayTransactionNo,
                              Instant paidAt, String responsePayload) {
        transaction.markSucceeded(gatewayTransactionNo, paidAt, responsePayload);
        // Audit entity của chính module này; RentalOrder/StorageUnit do booking tự ghi.
        audit.record(new AuditEntry(AuditAction.PAYMENT_SUCCEEDED,
                AuditEntityType.PAYMENT_TRANSACTION, String.valueOf(transaction.getId()),
                Map.of("status", "Pending"),
                Map.of("status", "Success", "gatewayTransactionNo",
                        String.valueOf(transaction.getGatewayTransactionNo())),
                null, null));
        events.publishEvent(new PaymentSucceeded(transaction.getInvoiceId(), transaction.getVnpTxnRef(),
                transaction.getAmount(), transaction.getPaidAt()));
    }

    private void applyFailure(PaymentTransaction transaction, String reason, String responsePayload) {
        transaction.markFailed(reason, responsePayload);
        audit.record(new AuditEntry(AuditAction.PAYMENT_FAILED,
                AuditEntityType.PAYMENT_TRANSACTION, String.valueOf(transaction.getId()),
                Map.of("status", "Pending"), Map.of("status", "Failed"), reason, null));
        events.publishEvent(new PaymentFailed(transaction.getInvoiceId(), transaction.getVnpTxnRef(),
                transaction.getAmount(), reason));
    }

    /** Cổng chỉ coi là thành công khi cả hai mã đều {@code 00}. */
    private boolean gatewayReportedSuccess(Map<String, String> params) {
        return SUCCESS_CODE.equals(params.get("vnp_ResponseCode"))
                && SUCCESS_CODE.equals(params.get("vnp_TransactionStatus"));
    }

    private boolean amountMatches(String rawAmount, BigDecimal expected) {
        if (rawAmount == null) {
            return false;
        }
        try {
            BigDecimal received = new BigDecimal(rawAmount).divide(AMOUNT_SCALE);
            return received.compareTo(expected) == 0;
        } catch (ArithmeticException | NumberFormatException e) {
            LOG.warn("IPN mang vnp_Amount không đọc được: {}", rawAmount);
            return false;
        }
    }

    /** Raw payload để đối soát — JSON nếu serialize được, fallback {@code toString} để không mất dữ liệu. */
    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            LOG.warn("Không serialize được payload IPN sang JSON, lưu dạng toString", e);
            return params.toString();
        }
    }

    /**
     * VNPay gửi PayDate dạng {@code yyyyMMddHHmmss} (GMT+7). Parse fail ở nhánh Success là anomaly
     * hiếm (chữ ký đã hợp lệ nên payload là thật từ VNPay) — log ERROR và fallback về **thời điểm
     * nhận IPN**. Lưu ý: đây là thời điểm nhận, không phải thời điểm trả tiền thật; {@code paid_at}
     * chỉ để hiển thị/đối chiếu, **không dùng để phán kịp/trễ deadline**.
     */
    private Instant parsePayDate(String rawPayDate) {
        if (rawPayDate == null) {
            LOG.error("IPN Success thiếu vnp_PayDate, fallback về thời điểm nhận IPN");
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(rawPayDate, PAY_DATE).atZone(VNPAY_ZONE).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            LOG.error("IPN mang vnp_PayDate không đọc được (raw='{}'), fallback về thời điểm nhận IPN",
                    rawPayDate);
            return Instant.now();
        }
    }
}
