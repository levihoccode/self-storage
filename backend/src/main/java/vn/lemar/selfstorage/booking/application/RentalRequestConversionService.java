package vn.lemar.selfstorage.booking.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.booking.domain.ProposalFeedback;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;
import vn.lemar.selfstorage.booking.repository.ConversionGuardRepository;
import vn.lemar.selfstorage.booking.repository.ProposalFeedbackRepository;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;
import vn.lemar.selfstorage.facility.application.StorageUnitQuery;
import vn.lemar.selfstorage.facility.application.StorageUnitView;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.identity.application.VerifiedAccount;
import vn.lemar.selfstorage.notification.application.NotificationRecipient;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationType;
import vn.lemar.selfstorage.pricing.application.PolicyQuery;

/**
 * B2 — convert {@code RentalRequest} đã duyệt sang {@code RentalOrder(Pending)} +
 * {@code ProposalFeedback(Pending)} cho customer đã xác minh email
 * ({@code draft.md} › 1.1 "email đã có tài khoản", 1.2 Case A).
 *
 * <p>Hai điểm kích hoạt, đều nội bộ (không có endpoint):
 * <ul>
 *   <li>{@link #convertOnApproval} — B1 gọi trong chính transaction FM duyệt.</li>
 *   <li>{@link #claimForVerifiedAccount} — luồng xác minh email (A3) gọi sau khi set
 *       {@code email_verified_at}.</li>
 * </ul>
 * Cả hai khoá advisory theo account trước khi kiểm trùng, nên không thể tạo hai đơn/proposal
 * đang hoạt động cho cùng (account, khoang) kể cả khi chạy đồng thời. Email đi qua
 * {@link NotificationService} — gửi sau commit, lỗi gửi không rollback conversion.
 */
@Service
public class RentalRequestConversionService {

    /** Spec {@code draft.md} › Các tham số sử dụng trong Flow 1. */
    static final String ORDER_EXPIRY_DAYS = "order.expiry_days";

    private static final Logger LOG = LoggerFactory.getLogger(RentalRequestConversionService.class);

    private final RentalRequestRepository requestRepository;
    private final RentalOrderRepository orderRepository;
    private final ProposalFeedbackRepository proposalRepository;
    private final ConversionGuardRepository conversionGuard;
    private final AccountQueryService accountQueryService;
    private final StorageUnitQuery storageUnitQuery;
    private final PolicyQuery policyQuery;
    private final NotificationService notificationService;
    private final AuditRecorder auditRecorder;
    private final String frontendUrl;

    public RentalRequestConversionService(RentalRequestRepository requestRepository,
                                          RentalOrderRepository orderRepository,
                                          ProposalFeedbackRepository proposalRepository,
                                          ConversionGuardRepository conversionGuard,
                                          AccountQueryService accountQueryService,
                                          StorageUnitQuery storageUnitQuery, PolicyQuery policyQuery,
                                          NotificationService notificationService, AuditRecorder auditRecorder,
                                          @Value("${app.frontend-url}") String frontendUrl) {
        this.requestRepository = requestRepository;
        this.orderRepository = orderRepository;
        this.proposalRepository = proposalRepository;
        this.conversionGuard = conversionGuard;
        this.accountQueryService = accountQueryService;
        this.storageUnitQuery = storageUnitQuery;
        this.policyQuery = policyQuery;
        this.notificationService = notificationService;
        this.auditRecorder = auditRecorder;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /**
     * Trigger 1 — FM vừa duyệt request. Email đã thuộc account xác minh thì convert ngay; chưa có
     * thì trả {@link ConversionResult.Outcome#NO_VERIFIED_ACCOUNT} và request giữ {@code Approved}
     * (caller gửi email "chưa có tài khoản" của Flow 1.1).
     *
     * <p>Không ghi AuditLog cho bước convert ở trigger này — catalog chưa có action phù hợp, chờ
     * spec-gap #132. Bản ghi {@code RENTAL_REQUEST_APPROVED} do B1 ghi.
     *
     * @throws IllegalArgumentException request không tồn tại
     */
    @Transactional
    public ConversionResult convertOnApproval(Long requestId) {
        // Chỉ đọc email (không load entity) để entity được load lần đầu sau khi đã khoá — tránh dùng
        // trạng thái cũ. Thứ tự khoá giống claim: account trước, row request sau.
        String email = requestRepository.findEmailById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy RentalRequest " + requestId));
        Optional<VerifiedAccount> account = accountQueryService.findVerifiedByEmail(email);
        if (account.isEmpty()) {
            return ConversionResult.skipped(requestId, ConversionResult.Outcome.NO_VERIFIED_ACCOUNT);
        }
        conversionGuard.lockAccount(account.get().id());
        RentalRequest request = requestRepository.findByIdForUpdate(requestId).orElseThrow();
        return convert(request, account.get(), Instant.now(), false);
    }

    /**
     * Trigger 2 — customer vừa xác minh email: claim mọi request {@code Approved} còn hạn khớp
     * email (spec 1.2 Case A). Account chưa xác minh → không claim gì.
     *
     * @return kết quả theo từng request còn hạn (rỗng nếu không có — Case B)
     */
    @Transactional
    public List<ConversionResult> claimForVerifiedAccount(Long accountId) {
        Optional<VerifiedAccount> account = accountQueryService.findVerifiedById(accountId);
        if (account.isEmpty()) {
            return List.of();
        }
        conversionGuard.lockAccount(accountId);
        Instant now = Instant.now();
        return requestRepository.findClaimableForUpdate(account.get().email(), now).stream()
                .map(request -> convert(request, account.get(), now, true))
                .toList();
    }

    private ConversionResult convert(RentalRequest request, VerifiedAccount account, Instant now, boolean claimed) {
        if (!request.isConvertibleAt(now)) {
            return ConversionResult.skipped(request.getId(), ConversionResult.Outcome.NOT_CONVERTIBLE);
        }
        Long unitId = request.getUnitId();
        if (unitId == null) {
            throw new IllegalStateException("RentalRequest " + request.getId() + " Approved nhưng chưa có unit_id");
        }
        if (conversionGuard.hasActiveConversion(account.id(), unitId)) {
            LOG.info("Bỏ qua convert request {}: account {} đã có đơn/proposal đang hoạt động cho khoang {}",
                    request.getId(), account.id(), unitId);
            return ConversionResult.skipped(request.getId(), ConversionResult.Outcome.DUPLICATE_UNIT);
        }
        StorageUnitView unit = storageUnitQuery.find(unitId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy StorageUnit " + unitId));
        Instant expiresAt = now.plus(policyQuery.requireInt(ORDER_EXPIRY_DAYS), ChronoUnit.DAYS);

        // saveAndFlush: hasActiveConversion đọc bằng JDBC, phải thấy đơn vừa tạo ở vòng lặp trước.
        RentalOrder order = orderRepository.saveAndFlush(RentalOrder.open(request.getId(), account.id(), expiresAt));
        ProposalFeedback proposal = proposalRepository.saveAndFlush(ProposalFeedback.propose(order, unitId));
        request.markConverted();
        requestRepository.save(request);

        if (claimed) {
            auditRecorder.record(new AuditEntry(AuditAction.RENTAL_REQUEST_CLAIMED, AuditEntityType.RENTAL_REQUEST,
                    String.valueOf(request.getId()),
                    Map.of("status", RentalRequestStatus.APPROVED.dbValue()),
                    Map.of("status", request.getStatus().dbValue(), "orderId", order.getId(),
                            "proposalId", proposal.getId()),
                    null, account.id()));
        }
        notificationService.notify(account.id(), account.email(), NotificationRecipient.CUSTOMER,
                NotificationType.RENTAL_REQUEST_APPROVED,
                Map.of("unitCode", unit.code(), "proposalLink", frontendUrl + "/proposals"), order.getId());
        return ConversionResult.converted(request.getId(), order.getId(), proposal.getId());
    }
}
