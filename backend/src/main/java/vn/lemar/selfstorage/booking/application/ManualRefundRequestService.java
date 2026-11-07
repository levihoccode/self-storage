package vn.lemar.selfstorage.booking.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.booking.application.exception.InvoiceNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RefundNotAllowedException;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.InvoiceRefundStatus;
import vn.lemar.selfstorage.booking.repository.BookingReadRepository;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.payment.application.RefundRequestService;
import vn.lemar.selfstorage.payment.application.dto.CreateRefundRequest;
import vn.lemar.selfstorage.payment.application.dto.RefundRequestView;
import vn.lemar.selfstorage.booking.application.exception.RefundRequestValidationException;

/**
 * Xác định facility cho refund request thủ công rồi giao payment tạo obligation. Không ghi
 * refund_transactions và không đổi invoice state — handover sẽ là bước sau.
 */
@Service
public class ManualRefundRequestService {

    private static final String REQUEST_PERMISSION = "refund_transaction.request";

    private final InvoiceRepository invoices;
    private final BookingReadRepository readRepository;
    private final RefundRequestService refundRequests;
    private final Access access;
    private final CurrentAccountProvider currentAccount;

    public ManualRefundRequestService(InvoiceRepository invoices, BookingReadRepository readRepository,
            RefundRequestService refundRequests, Access access,
            CurrentAccountProvider currentAccount) {
        this.invoices = invoices;
        this.readRepository = readRepository;
        this.refundRequests = refundRequests;
        this.access = access;
        this.currentAccount = currentAccount;
    }

    @Transactional
    public RefundRequestView create(CreateRefundRequest input) {
        Long invoiceId = input.invoiceId();
        Long orderId = input.orderId();
        Long facilityId;

        if (invoiceId != null) {
            Invoice invoice = invoices.findByIdForUpdate(invoiceId)
                    .orElseThrow(InvoiceNotFoundException::new);
            if (invoice.getRefundStatus() != InvoiceRefundStatus.NONE) {
                throw new RefundNotAllowedException(
                        "Hoá đơn đã có nghĩa vụ hoàn tiền");
            }
            if (invoice.getOrderId() == null) {
                throw new RefundRequestValidationException(
                        "Hoá đơn không gắn đơn thuê để xác định cơ sở");
            }
            if (orderId != null && !orderId.equals(invoice.getOrderId())) {
                throw new RefundRequestValidationException(
                        "orderId không khớp với hoá đơn");
            }
            orderId = invoice.getOrderId();
            facilityId = facilityOf(orderId);
        } else {
            if (orderId == null) {
                throw new RefundRequestValidationException(
                        "Phải có invoiceId hoặc orderId");
            }
            facilityId = facilityOf(orderId);
        }

        access.canAnyFacility(facilityId, REQUEST_PERMISSION);
        Long actorId = currentAccount.getCurrentAccountId();
        CreateRefundRequest withOrder = new CreateRefundRequest(invoiceId, orderId,
                input.reasonCode(), input.reasonNote(), input.calculationVersion(),
                input.calculationInput(), input.lines());
        return refundRequests.create(withOrder, actorId);
    }

    private Long facilityOf(Long orderId) {
        return readRepository.findOrder(orderId)
                .map(BookingReadRepository.OrderRow::facilityId)
                .orElseThrow(() -> new RefundRequestValidationException(
                        "Không tìm thấy đơn thuê"));
    }
}
