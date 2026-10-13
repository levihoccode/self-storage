package vn.lemar.selfstorage.booking.application;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.booking.application.exception.ProposalConflictException;
import vn.lemar.selfstorage.booking.application.exception.ProposalForbiddenException;
import vn.lemar.selfstorage.booking.application.exception.ProposalNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.ProposalValidationException;
import vn.lemar.selfstorage.booking.application.exception.RentalOrderNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitNotFoundException;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.ProposalFeedback;
import vn.lemar.selfstorage.booking.domain.ProposalStatus;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.repository.ProposalFeedbackRepository;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.facility.application.StorageUnitQuery;
import vn.lemar.selfstorage.facility.application.StorageUnitView;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.notification.application.NotificationType;
import vn.lemar.selfstorage.pricing.application.PricingQuery;

/**
 * Flow 1.3 — khách đồng ý / từ chối đề xuất khoang, FM đề xuất lại ({@code specs/draft.md} › 1.3), theo
 * các điểm chốt ở issue #113 (lệch spec có chủ đích, spec sẽ cập nhật sau review):
 * <ul>
 *   <li>Proposal dùng {@code Canceled} thay {@code Expired}.</li>
 *   <li>Tiền cọc = giá thuê hiệu lực × {@code invoice.deposit_months}.</li>
 *   <li>Huỷ đơn tại lần từ chối thứ 3: {@code số Rejected + 1 >= proposal.max_rejection_count}.</li>
 * </ul>
 */
@Service
public class ProposalService {

    static final String PERMISSION_REPROPOSE = "proposal_feedback.repropose";
    static final String PERMISSION_OVERRIDE = "proposal_feedback.override";
    static final String POLICY_MAX_REJECTION = "proposal.max_rejection_count";
    static final String CANCEL_REASON_TOO_MANY_REJECTIONS = "Từ chối quá số khoang cho phép";

    private final ProposalFeedbackRepository proposalRepository;
    private final RentalOrderRepository orderRepository;
    private final StorageUnitQuery storageUnitQuery;
    private final PricingQuery pricingQuery;
    private final Access access;
    private final AuditRecorder auditRecorder;
    private final OrderNotifier orderNotifier;
    private final DepositInvoiceIssuer depositInvoiceIssuer;
    private final RentalOrderCancellation orderCancellation;
    private final String frontendUrl;

    ProposalService(ProposalFeedbackRepository proposalRepository, RentalOrderRepository orderRepository,
                    StorageUnitQuery storageUnitQuery, PricingQuery pricingQuery, Access access,
                    AuditRecorder auditRecorder, OrderNotifier orderNotifier,
                    DepositInvoiceIssuer depositInvoiceIssuer, RentalOrderCancellation orderCancellation,
                    @Value("${app.frontend-url}") String frontendUrl) {
        this.proposalRepository = proposalRepository;
        this.orderRepository = orderRepository;
        this.storageUnitQuery = storageUnitQuery;
        this.pricingQuery = pricingQuery;
        this.access = access;
        this.auditRecorder = auditRecorder;
        this.orderNotifier = orderNotifier;
        this.depositInvoiceIssuer = depositInvoiceIssuer;
        this.orderCancellation = orderCancellation;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /**
     * Khách đồng ý proposal: {@code Agreed}, gán khoang vào đơn, tạo hoá đơn cọc. Khoang hết
     * {@code Available} → proposal {@code Canceled} + báo FM {@code PROPOSAL_REPROPOSAL_REQUIRED}, trả
     * {@link AgreeResult.Outcome#UNIT_UNAVAILABLE}.
     */
    @Transactional
    public AgreeResult agree(Long proposalId, Long customerAccountId) {
        ProposalFeedback proposal = lockPendingProposalOf(proposalId, customerAccountId);
        RentalOrder order = orderRepository.findById(proposal.getOrderId())
                .orElseThrow(RentalOrderNotFoundException::new);
        StorageUnitView unit = storageUnitQuery.lock(proposal.getUnitId())
                .orElseThrow(StorageUnitNotFoundException::new);

        if (!unit.isAvailable()) {
            proposal.cancel();
            orderNotifier.notifyFacilityManager(order.getId(), NotificationType.PROPOSAL_REPROPOSAL_REQUIRED,
                    Map.of("orderCode", OrderNotifier.orderCode(order), "unitCode", unit.code()));
            return new AgreeResult(AgreeResult.Outcome.UNIT_UNAVAILABLE, ProposalView.of(proposal), null);
        }

        proposal.agree();
        order.assignUnit(proposal.getUnitId());
        auditRecorder.record(new AuditEntry(AuditAction.PROPOSAL_AGREED, AuditEntityType.PROPOSAL_FEEDBACK,
                String.valueOf(proposal.getId()), Map.of("status", ProposalStatus.PENDING.dbValue()),
                Map.of("status", proposal.getStatus().dbValue(), "unitId", proposal.getUnitId()), null,
                customerAccountId));
        Invoice invoice = depositInvoiceIssuer.issue(order, unit, customerAccountId);
        return new AgreeResult(AgreeResult.Outcome.AGREED, ProposalView.of(proposal), InvoiceView.of(invoice));
    }

    /**
     * Khách từ chối proposal kèm lý do. Chưa chạm ngưỡng → báo FM {@code PROPOSAL_REJECTED}; chạm ngưỡng (lần
     * thứ {@code proposal.max_rejection_count}) → Cancellation Cascade, lý do "Từ chối quá số khoang cho phép".
     */
    @Transactional
    public RejectResult reject(Long proposalId, Long customerAccountId, String note) {
        if (note == null || note.isBlank()) {
            throw new ProposalValidationException("Vui lòng nhập lý do từ chối");
        }
        ProposalFeedback proposal = lockPendingProposalOf(proposalId, customerAccountId);
        // Khoá đơn để việc đếm số lần từ chối và cập nhật nằm gọn trong một transaction.
        RentalOrder order = orderRepository.findByIdForUpdate(proposal.getOrderId())
                .orElseThrow(RentalOrderNotFoundException::new);
        String unitCode = storageUnitQuery.find(proposal.getUnitId())
                .map(StorageUnitView::code)
                .orElseThrow(StorageUnitNotFoundException::new);
        long previousRejections = proposalRepository.countByOrderIdAndStatus(order.getId(), ProposalStatus.REJECTED);
        boolean reachesLimit = previousRejections + 1 >= pricingQuery.intPolicy(POLICY_MAX_REJECTION);

        proposal.reject(note.strip());
        auditRecorder.record(new AuditEntry(AuditAction.PROPOSAL_REJECTED, AuditEntityType.PROPOSAL_FEEDBACK,
                String.valueOf(proposal.getId()), Map.of("status", ProposalStatus.PENDING.dbValue()),
                Map.of("status", proposal.getStatus().dbValue()), proposal.getNote(), customerAccountId));

        if (reachesLimit) {
            orderCancellation.cancel(order, CANCEL_REASON_TOO_MANY_REJECTIONS, customerAccountId);
            return new RejectResult(ProposalView.of(proposal), true);
        }
        orderNotifier.notifyFacilityManager(order.getId(), NotificationType.PROPOSAL_REJECTED,
                Map.of("unitCode", unitCode, "reason", proposal.getNote()));
        return new RejectResult(ProposalView.of(proposal), false);
    }

    /**
     * FM đề xuất khoang khác cho đơn. Khoang khách đã từ chối trong cùng đơn chỉ được chọn lại khi
     * FM có quyền override và nhập lý do — lý do ghi vào AuditLog.
     *
     * @param overrideReason bắt buộc khi {@code unitId} đã bị khách từ chối; bỏ qua trong trường hợp khác
     */
    @Transactional
    public ProposalView repropose(Long orderId, Long unitId, String overrideReason, Long actorAccountId) {
        Long facilityId = orderRepository.findFacilityId(orderId).orElseThrow(RentalOrderNotFoundException::new);
        access.can(PERMISSION_REPROPOSE, facilityId);

        RentalOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow(RentalOrderNotFoundException::new);
        if (order.getStatus().isClosed()) {
            throw new ProposalConflictException("Đơn thuê đã đóng, không thể đề xuất khoang");
        }
        // Spec 1.3 chưa nói proposal Pending cũ xử lý ra sao khi đề xuất lại — chặn tới khi chốt.
        if (proposalRepository.existsByOrderIdAndStatus(orderId, ProposalStatus.PENDING)) {
            throw new ProposalConflictException("Đơn đang có đề xuất chờ khách phản hồi");
        }

        StorageUnitView unit = storageUnitQuery.lock(unitId)
                .filter(found -> found.facilityId().equals(facilityId))
                .orElseThrow(StorageUnitNotFoundException::new);
        if (!unit.isAvailable()) {
            throw new ProposalConflictException("Khoang " + unit.code() + " không còn trống");
        }

        String reason = null;
        List<Long> rejectedUnitIds = proposalRepository.findUnitIdsByOrderIdAndStatus(orderId,
                ProposalStatus.REJECTED);
        if (rejectedUnitIds.contains(unitId)) {
            if (overrideReason == null || overrideReason.isBlank()) {
                throw new ProposalValidationException(
                        "Khách đã từ chối khoang " + unit.code() + ", cần nhập lý do để đề xuất lại");
            }
            access.can(PERMISSION_OVERRIDE, facilityId);
            reason = overrideReason.strip();
        }

        ProposalFeedback proposal = proposalRepository.save(ProposalFeedback.propose(order, unitId));
        auditRecorder.record(new AuditEntry(AuditAction.PROPOSAL_REPROPOSED, AuditEntityType.PROPOSAL_FEEDBACK,
                String.valueOf(proposal.getId()), null,
                Map.of("status", proposal.getStatus().dbValue(), "unitId", unitId, "orderId", orderId), reason,
                actorAccountId));
        orderNotifier.notifyCustomer(order, NotificationType.PROPOSAL_REPROPOSED,
                Map.of("confirmLink", frontendUrl + "/proposals"));
        return ProposalView.of(proposal);
    }

    private ProposalFeedback lockPendingProposalOf(Long proposalId, Long customerAccountId) {
        ProposalFeedback proposal = proposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(ProposalNotFoundException::new);
        if (!proposal.getCustomerId().equals(customerAccountId)) {
            throw new ProposalForbiddenException();
        }
        if (!proposal.isPending()) {
            throw new ProposalConflictException("Đề xuất này đã được xử lý");
        }
        return proposal;
    }
}
