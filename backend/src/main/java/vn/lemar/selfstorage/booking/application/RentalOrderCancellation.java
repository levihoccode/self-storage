package vn.lemar.selfstorage.booking.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.InvoiceStatus;
import vn.lemar.selfstorage.booking.domain.ProposalFeedback;
import vn.lemar.selfstorage.booking.domain.ProposalStatus;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.booking.repository.ProposalFeedbackRepository;
import vn.lemar.selfstorage.facility.application.StorageUnitService;
import vn.lemar.selfstorage.notification.application.NotificationType;

/**
 * RentalOrder Cancellation Cascade ({@code specs/db-table-draft.md} › Cancellation Cascade), phần thuộc
 * booking. Chạy trong transaction của caller, đơn đã được caller khoá.
 *
 * <p>Chưa xử lý (chưa có code ở module tương ứng, booking cũng không được gọi sang handover/tenancy):
 * {@code RentalContract Draft/Signed -> Canceled}, {@code HandoverRecord IN_PROGRESS -> CANCELED},
 * {@code Appointment Pending -> Canceled}, xử lý tiền cọc theo Flow 4.
 */
@Component
class RentalOrderCancellation {

    static final String OUTCOME_CANCELED = "đã bị hủy";

    private final ProposalFeedbackRepository proposalRepository;
    private final InvoiceRepository invoiceRepository;
    private final StorageUnitService storageUnitService;
    private final AuditRecorder auditRecorder;
    private final OrderNotifier orderNotifier;

    RentalOrderCancellation(ProposalFeedbackRepository proposalRepository, InvoiceRepository invoiceRepository,
                            StorageUnitService storageUnitService, AuditRecorder auditRecorder,
                            OrderNotifier orderNotifier) {
        this.proposalRepository = proposalRepository;
        this.invoiceRepository = invoiceRepository;
        this.storageUnitService = storageUnitService;
        this.auditRecorder = auditRecorder;
        this.orderNotifier = orderNotifier;
    }

    /**
     * @param actorAccountId người gây ra việc huỷ; {@code null} = hệ thống/cron
     */
    void cancel(RentalOrder order, String reason, Long actorAccountId) {
        String oldStatus = order.getStatus().dbValue();
        // Chụp trước khi đổi status: đơn chỉ giữ khoang khi đang ở Deposited/Scheduled/InProgress.
        boolean heldUnit = order.holdsUnit();
        order.cancel(reason);
        record(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.RENTAL_ORDER, order.getId(),
                Map.of("status", oldStatus),
                Map.of("status", order.getStatus().dbValue(), "cancelReason", reason), reason, actorAccountId);

        for (ProposalFeedback proposal : proposalRepository.findByOrderIdAndStatusIn(order.getId(),
                List.of(ProposalStatus.PENDING, ProposalStatus.AGREED))) {
            String before = proposal.getStatus().dbValue();
            proposal.cancel();
            record(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.PROPOSAL_FEEDBACK, proposal.getId(),
                    Map.of("status", before), Map.of("status", proposal.getStatus().dbValue()), reason,
                    actorAccountId);
        }

        for (Invoice invoice : invoiceRepository.findByOrderIdAndStatus(order.getId(), InvoiceStatus.UNPAID)) {
            invoice.cancel();
            record(AuditAction.INVOICE_CANCELED, AuditEntityType.INVOICE, invoice.getId(),
                    Map.of("status", InvoiceStatus.UNPAID.dbValue()), Map.of("status", invoice.getStatus().dbValue()),
                    reason, actorAccountId);
        }

        // Chỉ nhả khoang khi đơn thực sự đang giữ nó: nhiều đơn có thể cùng unit_id (đã đồng ý, chưa cọc),
        // trả khoang của đơn khác đã cọc là sai.
        if (heldUnit && order.getUnitId() != null
                && storageUnitService.releaseIfReserved(order.getUnitId())) {
            record(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.STORAGE_UNIT, order.getUnitId(),
                    Map.of("status", "Reserved"), Map.of("status", "Available"), reason, actorAccountId);
        }

        Map<String, String> values = Map.of("orderCode", OrderNotifier.orderCode(order),
                "outcome", OUTCOME_CANCELED, "reason", reason);
        orderNotifier.notifyCustomer(order, NotificationType.RENTAL_ORDER_CANCELED, values);
        orderNotifier.notifyFacilityManager(order.getId(), NotificationType.RENTAL_ORDER_CANCELED, values);
    }

    private void record(AuditAction action, AuditEntityType type, Long entityId, Object oldValue, Object newValue,
                        String reason, Long actorAccountId) {
        auditRecorder.record(new AuditEntry(action, type, String.valueOf(entityId), oldValue, newValue, reason,
                actorAccountId));
    }
}
