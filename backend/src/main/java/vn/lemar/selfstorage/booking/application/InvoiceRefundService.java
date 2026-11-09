package vn.lemar.selfstorage.booking.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.booking.application.dto.RefundConfirmRequest;
import vn.lemar.selfstorage.booking.application.dto.RefundDisputeRequest;
import vn.lemar.selfstorage.booking.application.dto.RefundHandoverRequest;
import vn.lemar.selfstorage.booking.application.event.RefundAwaitingConfirmation;
import vn.lemar.selfstorage.booking.application.event.RefundDisputed;
import vn.lemar.selfstorage.booking.application.exception.InvoiceNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RefundNotAllowedException;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.InvoiceRefundStatus;
import vn.lemar.selfstorage.booking.repository.BookingReadRepository;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.payment.application.RefundObligationService;
import vn.lemar.selfstorage.payment.application.RefundTransactionService;
import vn.lemar.selfstorage.payment.application.dto.RefundDisputeResult;
import vn.lemar.selfstorage.payment.application.dto.RefundTransactionView;
import vn.lemar.selfstorage.payment.application.RefundReason;

/**
 * Điều phối hoàn tiền thủ công trên invoice. Ledger tiền ra nằm ở payment; invoice state và quyền
 * facility nằm ở booking. Không có bước FM duyệt giữa FS đưa tiền và khách xác nhận.
 */
@Service
public class InvoiceRefundService {

    private static final String HANDOVER_PERMISSION = "refund_transaction.handover";
    private static final String FM_REFUND_PERMISSION = "payment_transaction.refund";

    private final InvoiceRepository invoices;
    private final BookingReadRepository readRepository;
    private final RefundTransactionService refunds;
    private final RefundObligationService refundObligations;
    private final Access access;
    private final CurrentAccountProvider currentAccount;
    private final ApplicationEventPublisher events;

    public InvoiceRefundService(InvoiceRepository invoices, BookingReadRepository readRepository,
            RefundTransactionService refunds, RefundObligationService refundObligations,
            Access access, CurrentAccountProvider currentAccount,
            ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.readRepository = readRepository;
        this.refunds = refunds;
        this.refundObligations = refundObligations;
        this.access = access;
        this.currentAccount = currentAccount;
        this.events = events;
    }

    /** FS/FM xác nhận đã đưa tiền; invoice chuyển AwaitingConfirmation, chưa phải Refunded. */
    @Transactional
    public RefundTransactionView handover(Long invoiceId, RefundHandoverRequest request) {
        Invoice snapshot = invoices.findById(invoiceId).orElseThrow(InvoiceNotFoundException::new);
        BookingReadRepository.OrderRow order = orderOf(snapshot);
        requireHandoverPermission(order.facilityId());

        Invoice invoice = invoices.findByIdForUpdate(invoiceId).orElseThrow(InvoiceNotFoundException::new);
        if (invoice.getRefundStatus() != InvoiceRefundStatus.REQUIRED
                && invoice.getRefundStatus() != InvoiceRefundStatus.PROCESSING) {
            throw new RefundNotAllowedException("Hoá đơn không ở trạng thái cần hoàn tiền");
        }
        if (refundObligations.hasActiveForInvoice(invoiceId)) {
            throw new RefundNotAllowedException(
                    "Hoá đơn đang có nghĩa vụ hoàn tiền; không thể handover trực tiếp");
        }

        RefundReason reasonCode = normalizeReason(invoice.getRefundReason());
        RefundTransactionView refund = refunds.recordSuccess(invoiceId, request.sourceTransactionId(),
                invoice.getAmount(), request.method(), reasonCode, request.reasonNote(),
                request.gatewayRef(), request.evidenceUrl(), request.note(),
                currentAccount.getCurrentAccountId());

        invoice.markRefundAwaitingConfirmation();
        invoices.save(invoice);
        events.publishEvent(new RefundAwaitingConfirmation(invoiceId, invoice.getOrderId(),
                invoice.getCustomerId(), invoice.getCode(), refund.amount()));
        return refund;
    }

    /** Khách xác nhận đã nhận tiền; chỉ sau khi ledger đã có refund Success. */
    @Transactional
    public RefundTransactionView confirm(Long invoiceId, RefundConfirmRequest request) {
        Long customerId = currentAccount.getCurrentAccountId();
        Invoice invoice = lockOwnInvoice(invoiceId, customerId);

        if (invoice.getRefundStatus() == InvoiceRefundStatus.REFUNDED) {
            return refunds.confirm(invoiceId, request.refundTransactionId(), customerId);
        }
        if (invoice.getRefundStatus() != InvoiceRefundStatus.AWAITING_CONFIRMATION) {
            throw new RefundNotAllowedException(
                    "Chưa thể xác nhận: cơ sở chưa xác nhận đã đưa tiền hoàn");
        }

        RefundTransactionView refund = refunds.confirm(invoiceId, request.refundTransactionId(),
                customerId);
        invoice.markRefunded();
        invoices.save(invoice);
        return refund;
    }

    /** Khách báo chưa nhận; giữ AwaitingConfirmation để đối soát, không tạo refund lần hai. */
    @Transactional
    public RefundTransactionView dispute(Long invoiceId, RefundDisputeRequest request) {
        Long customerId = currentAccount.getCurrentAccountId();
        Invoice invoice = lockOwnInvoice(invoiceId, customerId);
        if (invoice.getRefundStatus() != InvoiceRefundStatus.AWAITING_CONFIRMATION) {
            throw new RefundNotAllowedException("Hoá đơn không ở trạng thái chờ xác nhận hoàn tiền");
        }
        RefundDisputeResult result = refunds.dispute(invoiceId, request.refundTransactionId(),
                request.note(), customerId);
        if (result.newlyDisputed()) {
            events.publishEvent(new RefundDisputed(invoiceId, invoice.getOrderId(), invoice.getCustomerId(),
                    invoice.getCode(), result.refund().amount(), request.note()));
        }
        return result.refund();
    }

    private Invoice lockOwnInvoice(Long invoiceId, Long customerId) {
        Invoice invoice = invoices.findByIdForUpdate(invoiceId).orElseThrow(InvoiceNotFoundException::new);
        if (!customerId.equals(invoice.getCustomerId())) {
            throw new InvoiceNotFoundException();
        }
        return invoice;
    }

    private BookingReadRepository.OrderRow orderOf(Invoice invoice) {
        if (invoice.getOrderId() == null) {
            throw new RefundNotAllowedException("Hoá đơn không gắn đơn thuê để xác định cơ sở");
        }
        return readRepository.findOrder(invoice.getOrderId())
                .orElseThrow(() -> new RefundNotAllowedException(
                        "Không tìm thấy đơn thuê của hoá đơn"));
    }

    private void requireHandoverPermission(Long facilityId) {
        access.canAnyFacility(facilityId, HANDOVER_PERMISSION, FM_REFUND_PERMISSION);
    }

    private static RefundReason normalizeReason(String reason) {
        return RefundReason.fromInvoiceReason(reason);
    }
}
