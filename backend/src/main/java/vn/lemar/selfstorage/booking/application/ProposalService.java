package vn.lemar.selfstorage.booking.application;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import vn.lemar.selfstorage.booking.domain.ProposalFeedback;
import vn.lemar.selfstorage.booking.domain.ProposalStatus;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.repository.ProposalFeedbackRepository;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.facility.application.FacilityAccess;
import vn.lemar.selfstorage.facility.application.StorageUnitQuery;
import vn.lemar.selfstorage.facility.application.StorageUnitView;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.notification.application.NotificationRecipient;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationType;

/**
 * Flow 1.3 — khách đồng ý / từ chối đề xuất khoang, FM đề xuất lại ({@code specs/draft.md} › 1.3).
 *
 * <p>Chưa làm, chờ trả lời câu hỏi spec ở issue #113:
 * <ul>
 *   <li>Tạo {@code Invoice(Deposit)} khi đồng ý — chưa rõ số tiền cọc lấy từ đâu.</li>
 *   <li>Huỷ đơn khi chạm {@code proposal.max_rejection_count} — chưa rõ lần thứ 3 có huỷ không.</li>
 * </ul>
 */
@Service
public class ProposalService {

    static final String PERMISSION_REPROPOSE = "proposal_feedback.repropose";
    static final String PERMISSION_OVERRIDE = "proposal_feedback.override";

    private static final Logger LOG = LoggerFactory.getLogger(ProposalService.class);

    private final ProposalFeedbackRepository proposalRepository;
    private final RentalOrderRepository orderRepository;
    private final StorageUnitQuery storageUnitQuery;
    private final FacilityAccess facilityAccess;
    private final Access access;
    private final AccountQueryService accountQueryService;
    private final NotificationService notificationService;
    private final AuditRecorder auditRecorder;
    private final String frontendUrl;

    public ProposalService(ProposalFeedbackRepository proposalRepository, RentalOrderRepository orderRepository,
                           StorageUnitQuery storageUnitQuery, FacilityAccess facilityAccess, Access access,
                           AccountQueryService accountQueryService, NotificationService notificationService,
                           AuditRecorder auditRecorder, @Value("${app.frontend-url}") String frontendUrl) {
        this.proposalRepository = proposalRepository;
        this.orderRepository = orderRepository;
        this.storageUnitQuery = storageUnitQuery;
        this.facilityAccess = facilityAccess;
        this.access = access;
        this.accountQueryService = accountQueryService;
        this.notificationService = notificationService;
        this.auditRecorder = auditRecorder;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /**
     * Khách đồng ý proposal. Khoang hết {@code Available} → proposal {@code Expired} + báo FM
     * {@code PROPOSAL_REPROPOSAL_REQUIRED}, trả {@link AgreeResult.Outcome#UNIT_UNAVAILABLE}.
     */
    @Transactional
    public AgreeResult agree(Long proposalId, Long customerAccountId) {
        ProposalFeedback proposal = lockPendingProposalOf(proposalId, customerAccountId);
        RentalOrder order = orderRepository.findById(proposal.getOrderId())
                .orElseThrow(RentalOrderNotFoundException::new);
        StorageUnitView unit = storageUnitQuery.lock(proposal.getUnitId())
                .orElseThrow(StorageUnitNotFoundException::new);

        if (!unit.isAvailable()) {
            proposal.expire();
            notifyFacilityManager(order.getId(), NotificationType.PROPOSAL_REPROPOSAL_REQUIRED,
                    Map.of("orderCode", orderCode(order), "unitCode", unit.code()));
            return new AgreeResult(AgreeResult.Outcome.UNIT_UNAVAILABLE, ProposalView.of(proposal));
        }

        proposal.agree();
        order.assignUnit(proposal.getUnitId());
        auditRecorder.record(new AuditEntry(AuditAction.PROPOSAL_AGREED, AuditEntityType.PROPOSAL_FEEDBACK,
                String.valueOf(proposal.getId()), Map.of("status", ProposalStatus.PENDING.dbValue()),
                Map.of("status", proposal.getStatus().dbValue(), "unitId", proposal.getUnitId()), null,
                customerAccountId));
        return new AgreeResult(AgreeResult.Outcome.AGREED, ProposalView.of(proposal));
    }

    /** Khách từ chối proposal kèm lý do; báo FM {@code PROPOSAL_REJECTED}. */
    @Transactional
    public ProposalView reject(Long proposalId, Long customerAccountId, String note) {
        if (note == null || note.isBlank()) {
            throw new ProposalValidationException("Vui lòng nhập lý do từ chối");
        }
        ProposalFeedback proposal = lockPendingProposalOf(proposalId, customerAccountId);
        String unitCode = storageUnitQuery.find(proposal.getUnitId())
                .map(StorageUnitView::code)
                .orElseThrow(StorageUnitNotFoundException::new);

        proposal.reject(note.strip());
        auditRecorder.record(new AuditEntry(AuditAction.PROPOSAL_REJECTED, AuditEntityType.PROPOSAL_FEEDBACK,
                String.valueOf(proposal.getId()), Map.of("status", ProposalStatus.PENDING.dbValue()),
                Map.of("status", proposal.getStatus().dbValue()), proposal.getNote(), customerAccountId));
        notifyFacilityManager(proposal.getOrderId(), NotificationType.PROPOSAL_REJECTED,
                Map.of("unitCode", unitCode, "reason", proposal.getNote()));
        return ProposalView.of(proposal);
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
        // Spec 1.3 chưa nói proposal Pending cũ xử lý ra sao khi đề xuất lại — chặn tới khi chốt (#113).
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
        notificationService.notify(order.getCustomerId(), accountQueryService.requireEmailById(order.getCustomerId()),
                NotificationRecipient.CUSTOMER, NotificationType.PROPOSAL_REPROPOSED,
                Map.of("confirmLink", frontendUrl + "/proposals"), orderId);
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

    private void notifyFacilityManager(Long orderId, NotificationType type, Map<String, ?> values) {
        Long facilityId = orderRepository.findFacilityId(orderId).orElseThrow(RentalOrderNotFoundException::new);
        facilityAccess.managerAccountId(facilityId).ifPresentOrElse(
                fmId -> notificationService.notify(fmId, accountQueryService.requireEmailById(fmId),
                        NotificationRecipient.FM, type, values, orderId),
                () -> LOG.warn("Cơ sở {} chưa gán FM — bỏ qua notification {} cho đơn {}", facilityId, type, orderId));
    }

    /** Spec RentalOrder: {@code id} dùng làm mã đơn hiển thị cho khách trong thông báo/email. */
    private static String orderCode(RentalOrder order) {
        return String.valueOf(order.getId());
    }
}
