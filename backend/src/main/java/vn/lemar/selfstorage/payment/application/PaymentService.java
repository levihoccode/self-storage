package vn.lemar.selfstorage.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.payment.application.dto.IpnResponse;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.application.event.PaymentFailed;
import vn.lemar.selfstorage.payment.application.event.PaymentSucceeded;
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

    public PaymentService(PaymentGateway gateway, PaymentTransactionRepository repository,
                          VnPayProperties properties, ApplicationEventPublisher events) {
        this.gateway = gateway;
        this.repository = repository;
        this.properties = properties;
        this.events = events;
    }

    /**
     * Ghi nhận một lượt thanh toán ở trạng thái {@code Pending} rồi trả URL để redirect khách.
     *
     * <p>Phải tạo bản ghi <em>trước</em> khi chuyển hướng: nếu khách đóng trình duyệt giữa chừng,
     * giao dịch vẫn còn dấu vết để đối soát.
     */
    @Transactional
    public String startPayment(Long invoiceId, PaymentRequest request) {
        PaymentTransaction transaction = new PaymentTransaction(
                invoiceId, request.txnRef(), request.amount(), request.orderInfo());
        repository.save(transaction);

        return gateway.buildPaymentUrl(request);
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

        if (!amountMatches(params.get("vnp_Amount"), transaction.getAmount())) {
            return IpnResponse.invalidAmount();
        }

        // Chốt chặn idempotency: VNPay retry tới 10 lần, lần thứ hai trở đi không được ghi lại.
        if (!transaction.isPending()) {
            return IpnResponse.alreadyConfirmed();
        }

        String responsePayload = params.toString();
        if (gatewayReportedSuccess(params)) {
            transaction.markSucceeded(
                    params.get("vnp_TransactionNo"), parsePayDate(params.get("vnp_PayDate")), responsePayload);
            events.publishEvent(new PaymentSucceeded(transaction.getInvoiceId(), txnRef,
                    transaction.getAmount(), transaction.getPaidAt()));
        } else {
            String reason = "vnp_ResponseCode=" + params.get("vnp_ResponseCode")
                    + ", vnp_TransactionStatus=" + params.get("vnp_TransactionStatus");
            transaction.markFailed(reason, responsePayload);
            events.publishEvent(new PaymentFailed(transaction.getInvoiceId(), txnRef,
                    transaction.getAmount(), reason));
        }
        return IpnResponse.success();
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

    private Instant parsePayDate(String rawPayDate) {
        if (rawPayDate == null) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(rawPayDate, PAY_DATE).atZone(VNPAY_ZONE).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            LOG.warn("IPN mang vnp_PayDate không đọc được: {}", rawPayDate);
            return Instant.now();
        }
    }
}
