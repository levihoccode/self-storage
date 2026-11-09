package vn.lemar.selfstorage.booking.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.booking.application.exception.ProposalConflictException;
import vn.lemar.selfstorage.booking.application.exception.RentalOrderNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RentalOrderValidationException;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.InvoiceRefundStatus;
import vn.lemar.selfstorage.booking.domain.InvoiceStatus;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.notification.application.NotificationType;

/**
 * Thao tác của FM trên đơn thuê ngoài luồng proposal — #142: khách không phản hồi proposal, FM liên hệ ngoài
 * hệ thống không được thì huỷ đơn ({@code specs/draft.md} › 1.3).
 */
@Service
public class RentalOrderService {

    static final String PERMISSION_CANCEL = "rental_order.cancel";

    static final String REFUND_REASON = "ORDER_CANCELED";

    private final RentalOrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final Access access;
    private final RentalOrderCancellation orderCancellation;
    private final AuditRecorder auditRecorder;
    private final OrderNotifier orderNotifier;

    RentalOrderService(RentalOrderRepository orderRepository, InvoiceRepository invoiceRepository,
                       Access access, RentalOrderCancellation orderCancellation,
                       AuditRecorder auditRecorder, OrderNotifier orderNotifier) {
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.access = access;
        this.orderCancellation = orderCancellation;
        this.auditRecorder = auditRecorder;
        this.orderNotifier = orderNotifier;
    }

    /**
     * FM huỷ đơn với lý do do FM nhập, chạy đủ Cancellation Cascade. Quyền
     * {@code rental_order.cancel} theo cơ sở của đơn; đơn đã đóng → 409.
     *
     * <p>{@code refundDeposit = true}: mỗi hoá đơn {@code Paid} của đơn chuyển
     * {@code refund_status = Required} để vào queue hoàn tiền của FM/FS; đơn chưa thanh toán cọc → 400.
     * Không tạo refund transaction — tiền ra phải do FS/FM handover và khách xác nhận.
     */
    @Transactional
    public RentalOrderView cancelByFacilityManager(Long orderId, String reason, String note,
                                                   boolean refundDeposit, Long actorAccountId) {
        Long facilityId = orderRepository.findFacilityId(orderId).orElseThrow(RentalOrderNotFoundException::new);
        access.can(PERMISSION_CANCEL, facilityId);

        RentalOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow(RentalOrderNotFoundException::new);
        if (order.getStatus().isClosed()) {
            throw new ProposalConflictException("Đơn thuê đã đóng, không thể huỷ");
        }
        List<Invoice> paidInvoices = refundDeposit
                ? invoiceRepository.findByOrderIdAndStatus(orderId, InvoiceStatus.PAID)
                : List.of();
        if (refundDeposit && paidInvoices.isEmpty()) {
            throw new RentalOrderValidationException("Đơn thuê chưa thanh toán cọc, không thể hoàn cọc");
        }
        String trimmedNote = note == null || note.isBlank() ? null : note.strip();
        orderCancellation.cancel(order, reason.strip(), actorAccountId, trimmedNote);
        for (Invoice invoice : paidInvoices) {
            if (invoice.getRefundStatus() != InvoiceRefundStatus.NONE) {
                continue;
            }
            invoice.markRefundRequired(REFUND_REASON);
            auditRecorder.record(new AuditEntry(AuditAction.INVOICE_REFUND_REQUIRED,
                    AuditEntityType.INVOICE, String.valueOf(invoice.getId()),
                    Map.of("status", InvoiceStatus.PAID.dbValue()),
                    Map.of("status", InvoiceStatus.PAID.dbValue(), "refundStatus", "Required",
                            "reason", REFUND_REASON),
                    reason.strip(), actorAccountId));
            Map<String, String> values = Map.of("orderCode", String.valueOf(orderId),
                    "amount", invoice.getAmount().toPlainString());
            orderNotifier.notifyFacilityManager(orderId, NotificationType.ORDER_CANCELED_REFUND_REQUIRED, values);
            orderNotifier.notifyCustomer(order, NotificationType.ORDER_CANCELED_REFUND_PENDING, values);
        }
        return RentalOrderView.of(order);
    }
}
