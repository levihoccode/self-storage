package vn.lemar.selfstorage.booking.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.domain.StorageUnit;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.booking.repository.PolicyReader;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.booking.repository.StorageUnitRepository;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationRecipient;
import vn.lemar.selfstorage.notification.application.NotificationType;
import vn.lemar.selfstorage.payment.application.event.PaymentFailed;
import vn.lemar.selfstorage.payment.application.event.PaymentSucceeded;

/**
 * Chốt đặt cọc khi cổng xác nhận — spec `draft.md` §1.4 nhánh "Thành công".
 *
 * <p><strong>Vì sao AFTER_COMMIT:</strong> event được publish <em>trong</em> transaction xử lý IPN
 * (`backend/docs/payment.md` › Quy tắc bất biến). Listener đồng bộ mặc định sẽ đọc phải state chưa
 * commit, và exception từ listener sẽ rollback luôn giao dịch IPN khiến VNPay retry đủ 10 lần.
 *
 * <p><strong>Vì sao REQUIRES_NEW:</strong> AFTER_COMMIT chạy khi transaction gốc đã đóng, tức
 * không còn transaction nào. {@link AuditRecorder} dùng propagation {@code MANDATORY} nên phải
 * mở transaction mới, nếu không là lỗi wiring.
 */
@Component
public class DepositPaymentListener {

    private static final Logger LOG = LoggerFactory.getLogger(DepositPaymentListener.class);

    private static final String HOLD_POLICY_KEY = "order.deposit_expiry_days";
    private static final int HOLD_FALLBACK_DAYS = 30;

    private final InvoiceRepository invoices;
    private final RentalOrderRepository orders;
    private final StorageUnitRepository units;
    private final NotificationService notifications;
    private final PolicyReader policies;
    private final AuditRecorder audit;

    public DepositPaymentListener(InvoiceRepository invoices, RentalOrderRepository orders,
            StorageUnitRepository units, NotificationService notifications,
            PolicyReader policies, AuditRecorder audit) {
        this.invoices = invoices;
        this.orders = orders;
        this.units = units;
        this.notifications = notifications;
        this.policies = policies;
        this.audit = audit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentSucceeded(PaymentSucceeded event) {
        Optional<Invoice> found = invoices.findById(event.invoiceId());
        if (found.isEmpty()) {
            // Giao dịch trỏ tới hóa đơn không tồn tại — tiền đã vào, phải đối soát tay.
            LOG.error("Thanh toán thành công nhưng không tìm thấy hóa đơn {} (vnp_TxnRef={})",
                    event.invoiceId(), event.vnpTxnRef());
            return;
        }
        Invoice invoice = found.get();
        if (!invoice.isUnpaid()) {
            // IPN lặp đã bị chặn ở payment; tới đây mà không còn Unpaid nghĩa là đã chốt rồi.
            LOG.info("Hóa đơn {} không còn ở trạng thái Unpaid, bỏ qua", invoice.getId());
            return;
        }

        invoice.markPaid();

        RentalOrder order = orders.findById(invoice.getOrderId()).orElseThrow();
        Instant holdUntil = Instant.now()
                .plus(Duration.ofDays(policies.intValue(HOLD_POLICY_KEY, HOLD_FALLBACK_DAYS)));
        order.markDeposited(holdUntil);
        audit.record(new AuditEntry(AuditAction.RENTAL_ORDER_DEPOSITED, AuditEntityType.RENTAL_ORDER,
                String.valueOf(order.getId()), Map.of("status", "Pending"),
                Map.of("status", "Deposited", "expiresAt", holdUntil.toString()), null, null));

        StorageUnit unit = units.findById(order.getUnitId()).orElseThrow();
        String previousStatus = unit.getStatus().dbValue();
        unit.reserve();
        audit.record(new AuditEntry(AuditAction.STORAGE_UNIT_RESERVED, AuditEntityType.STORAGE_UNIT,
                String.valueOf(unit.getId()), Map.of("status", previousStatus),
                Map.of("status", "Reserved"), null, null));

        notifications.notify(invoice.getCustomerId(), null, NotificationRecipient.CUSTOMER,
                NotificationType.DEPOSIT_PAYMENT_SUCCEEDED,
                Map.of("orderCode", String.valueOf(order.getId())),
                order.getId());
    }

    /**
     * Thất bại thì không phải gỡ khóa bằng tay: khóa suy ra từ giao dịch còn {@code Pending}, mà
     * payment đã chuyển sang {@code Failed} trước khi bắn event. Khoang giữ nguyên `Available`.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailed event) {
        LOG.info("Thanh toán thất bại cho hóa đơn {} (vnp_TxnRef={}): {}",
                event.invoiceId(), event.vnpTxnRef(), event.reason());
    }
}
