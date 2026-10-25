package vn.lemar.selfstorage.booking.application;

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
import vn.lemar.selfstorage.booking.domain.InvoiceStatus;
import vn.lemar.selfstorage.booking.domain.InvoiceType;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.domain.RentalOrderStatus;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.facility.application.StorageUnitQuery;
import vn.lemar.selfstorage.facility.application.StorageUnitService;
import vn.lemar.selfstorage.facility.application.StorageUnitView;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.notification.application.NotificationRecipient;
import vn.lemar.selfstorage.notification.application.NotificationService;
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
 * <p><strong>Vì sao REQUIRES_NEW:</strong> AFTER_COMMIT chạy khi transaction gốc đã đóng.
 * {@link AuditRecorder} và API của facility đều dùng propagation {@code MANDATORY} nên phải mở
 * transaction mới.
 *
 * <p><strong>Không đụng `RentalOrder.expires_at`:</strong> spec `db-table-draft.md` › RentalOrder
 * ghi hạn đơn "set một lần khi tạo đơn", và › StorageUnit ghi "hạn giữ kho là phần còn lại của
 * hạn đơn". Cọc xong chỉ đổi trạng thái.
 */
@Component
public class DepositPaymentListener {

    private static final Logger LOG = LoggerFactory.getLogger(DepositPaymentListener.class);

    private final InvoiceRepository invoices;
    private final RentalOrderRepository orders;
    private final StorageUnitQuery unitQuery;
    private final StorageUnitService units;
    private final NotificationService notifications;
    private final AccountQueryService accounts;
    private final AuditRecorder audit;

    public DepositPaymentListener(InvoiceRepository invoices, RentalOrderRepository orders,
            StorageUnitQuery unitQuery, StorageUnitService units, NotificationService notifications,
            AccountQueryService accounts, AuditRecorder audit) {
        this.invoices = invoices;
        this.orders = orders;
        this.unitQuery = unitQuery;
        this.units = units;
        this.notifications = notifications;
        this.accounts = accounts;
        this.audit = audit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentSucceeded(PaymentSucceeded event) {
        Optional<Invoice> found = invoices.findById(event.invoiceId());
        if (found.isEmpty()) {
            LOG.error("Thanh toán thành công nhưng không tìm thấy hóa đơn {} (vnp_TxnRef={})"
                    + " — cần đối soát tay", event.invoiceId(), event.vnpTxnRef());
            return;
        }
        Invoice invoice = found.get();

        // Listener này chỉ chốt hóa đơn cọc. Hóa đơn thuê/gia hạn gắn contract_id, không có
        // order_id — đi tiếp sẽ ném lỗi ở orders.findById(null).
        if (invoice.getType() != InvoiceType.DEPOSIT) {
            return;
        }

        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            reportAlreadySettled(invoice, event);
            return;
        }

        RentalOrder order = orders.findByIdForUpdate(invoice.getOrderId()).orElseThrow();

        // Khoang có thể đã đổi chủ trong lúc phiên còn mở: VNPay retry tới 10 lần × 5 phút, nên
        // IPN có thể về sau khi khách khác đã cọc xong khoang này.
        if (!units.reserveIfAvailable(order.getUnitId())) {
            reportUnitNoLongerAvailable(invoice, order, event);
            return;
        }

        invoice.markPaid();

        RentalOrderStatus previousStatus = order.getStatus();
        order.markDeposited();
        audit.record(new AuditEntry(AuditAction.RENTAL_ORDER_DEPOSITED, AuditEntityType.RENTAL_ORDER,
                String.valueOf(order.getId()), Map.of("status", previousStatus.dbValue()),
                Map.of("status", RentalOrderStatus.DEPOSITED.dbValue()), null, null));
        audit.record(new AuditEntry(AuditAction.STORAGE_UNIT_RESERVED, AuditEntityType.STORAGE_UNIT,
                String.valueOf(order.getUnitId()), Map.of("status", "Available"),
                Map.of("status", "Reserved"), null, null));

        notifyCustomer(invoice, order);
    }

    /**
     * Hóa đơn không còn {@code Unpaid}. {@code Paid} là IPN lặp — payment đã chặn ở mức giao dịch,
     * tới đây chỉ còn là nhiễu. {@code Canceled}/{@code Expired} thì khác hẳn: tiền đã vào thật
     * cho một hóa đơn không còn hiệu lực, phải có người hoàn lại.
     */
    private void reportAlreadySettled(Invoice invoice, PaymentSucceeded event) {
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            LOG.info("Hóa đơn {} đã Paid, bỏ qua IPN lặp (vnp_TxnRef={})",
                    invoice.getId(), event.vnpTxnRef());
            return;
        }
        LOG.error("Đã thu {} cho hóa đơn {} đang ở trạng thái {} (vnp_TxnRef={}) —"
                        + " tiền cần được hoàn thủ công",
                event.amount(), invoice.getId(), invoice.getStatus().dbValue(), event.vnpTxnRef());
    }

    /** Tiền đã vào nhưng khoang đã thuộc về người khác — đơn này không được chuyển Deposited. */
    private void reportUnitNoLongerAvailable(Invoice invoice, RentalOrder order, PaymentSucceeded event) {
        String status = unitQuery.lock(order.getUnitId()).map(StorageUnitView::status).orElse("không tồn tại");
        LOG.error("Đã thu {} cho hóa đơn {} nhưng khoang {} đang ở trạng thái {} (vnp_TxnRef={}) —"
                        + " đơn {} giữ nguyên {}, tiền cần được hoàn thủ công",
                event.amount(), invoice.getId(), order.getUnitId(), status, event.vnpTxnRef(),
                order.getId(), order.getStatus().dbValue());
    }

    private void notifyCustomer(Invoice invoice, RentalOrder order) {
        // recipientEmail bắt buộc: thiếu thì SmtpEmailSender bỏ qua gửi mail và khách chỉ có
        // thông báo trong app, trái yêu cầu "email + in-app" của backend/AGENTS.md.
        String email = accounts.requireEmailById(invoice.getCustomerId());
        notifications.notify(invoice.getCustomerId(), email, NotificationRecipient.CUSTOMER,
                NotificationType.DEPOSIT_PAYMENT_SUCCEEDED,
                Map.of("orderCode", String.valueOf(order.getId())), order.getId());
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
