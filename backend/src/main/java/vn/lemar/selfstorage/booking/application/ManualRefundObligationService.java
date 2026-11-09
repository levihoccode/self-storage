package vn.lemar.selfstorage.booking.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.booking.application.exception.InvoiceNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RentalOrderNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RefundNotAllowedException;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.InvoiceRefundStatus;
import vn.lemar.selfstorage.booking.domain.InvoiceStatus;
import vn.lemar.selfstorage.booking.repository.BookingReadRepository;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.payment.application.RefundObligationService;
import vn.lemar.selfstorage.payment.application.dto.CreateRefundObligation;
import vn.lemar.selfstorage.payment.application.dto.RefundObligationView;
import vn.lemar.selfstorage.booking.application.exception.RefundObligationValidationException;

/**
 * Xác định facility cho refund obligation thủ công rồi giao payment tạo obligation. Không ghi
 * refund_transactions và không đổi invoice state — handover sẽ là bước sau.
 */
@Service
public class ManualRefundObligationService {

    private static final String CREATE_PERMISSION = "refund_obligation.create";

    private final InvoiceRepository invoices;
    private final BookingReadRepository readRepository;
    private final RefundObligationService refundObligations;
    private final Access access;
    private final CurrentAccountProvider currentAccount;

    public ManualRefundObligationService(InvoiceRepository invoices, BookingReadRepository readRepository,
            RefundObligationService refundObligations, Access access,
            CurrentAccountProvider currentAccount) {
        this.invoices = invoices;
        this.readRepository = readRepository;
        this.refundObligations = refundObligations;
        this.access = access;
        this.currentAccount = currentAccount;
    }

    @Transactional
    public RefundObligationView create(CreateRefundObligation input) {
        Long invoiceId = input.invoiceId();
        Long orderId = input.orderId();
        Long facilityId;

        if (invoiceId != null) {
            Invoice invoice = invoices.findByIdForUpdate(invoiceId)
                    .orElseThrow(InvoiceNotFoundException::new);
            if (invoice.getStatus() != InvoiceStatus.PAID) {
                throw new RefundNotAllowedException("Hoá đơn chưa thanh toán");
            }
            if (invoice.getRefundStatus() != InvoiceRefundStatus.NONE) {
                throw new RefundNotAllowedException(
                        "Hoá đơn đã có nghĩa vụ hoàn tiền");
            }
            if (invoice.getOrderId() == null) {
                throw new RefundObligationValidationException(
                        "Hoá đơn không gắn đơn thuê để xác định cơ sở");
            }
            if (orderId != null && !orderId.equals(invoice.getOrderId())) {
                throw new RefundObligationValidationException(
                        "orderId không khớp với hoá đơn");
            }
            orderId = invoice.getOrderId();
            facilityId = facilityOf(orderId);
        } else {
            if (orderId == null) {
                throw new RefundObligationValidationException(
                        "Phải có invoiceId hoặc orderId");
            }
            facilityId = facilityOf(orderId);
        }

        access.canAnyFacility(facilityId, CREATE_PERMISSION);
        Long actorId = currentAccount.getCurrentAccountId();
        CreateRefundObligation withOrder = new CreateRefundObligation(invoiceId, orderId,
                input.reasonCode(), input.reasonNote(), input.calculationVersion(),
                input.calculationInput(), input.lines());
        return refundObligations.create(withOrder, actorId);
    }

    private Long facilityOf(Long orderId) {
        return readRepository.findOrder(orderId)
                .map(BookingReadRepository.OrderRow::facilityId)
                .orElseThrow(RentalOrderNotFoundException::new);
    }
}
