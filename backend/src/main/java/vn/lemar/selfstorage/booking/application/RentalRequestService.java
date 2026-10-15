package vn.lemar.selfstorage.booking.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.booking.application.dto.ApproveRentalRequestResponse;
import vn.lemar.selfstorage.booking.application.dto.CreateRentalRequestRequest;
import vn.lemar.selfstorage.booking.application.dto.RejectRentalRequestResponse;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestPageResponse;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestResponse;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestSummaryResponse;
import vn.lemar.selfstorage.booking.application.exception.FacilityNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestAlreadyHandledException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestDuplicateUnitException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestValidationException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitUnavailableException;
import vn.lemar.selfstorage.booking.application.exception.UnitTypeNotFoundException;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.domain.RentalRequestRejectReason;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;
import vn.lemar.selfstorage.facility.application.FacilityAccess;
import vn.lemar.selfstorage.facility.application.StorageUnitQueryService;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.identity.application.exception.AccountNotFoundException;
import vn.lemar.selfstorage.notification.EmailSender;
import vn.lemar.selfstorage.notification.application.NotificationEmailLayout;
import vn.lemar.selfstorage.notification.application.NotificationRecipient;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationTemplateCatalog;
import vn.lemar.selfstorage.notification.application.NotificationType;
import vn.lemar.selfstorage.pricing.application.PolicyQueryService;
import vn.lemar.selfstorage.pricing.application.UnitTypeQueryService;

/**
 * Tạo yêu cầu đặt kho từ form public (B1, spec draft.md §1.1).
 *
 * <p>NOTE(B1): chưa ghi AuditLog cho bản ghi mới — A8 (audit) chưa có code,
 * sẽ bổ sung khi A8 xong (chốt #126 mục 5).
 */
@Service
public class RentalRequestService {

    // Bảng facilities chưa có cột timezone (chốt #126 mục 3) — tạm dùng giờ VN.
    private static final ZoneId FACILITY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PENDING_EXPIRY_KEY = "request.pending_expiry_days";
    private static final String CLAIM_EXPIRY_KEY = "account.claim_ttl_days";
    private static final long EXPIRY_FALLBACK_DAYS = 7L;
    private static final DateTimeFormatter VI_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RentalRequestRepository repository;
    private final FacilityAccess facilityAccess;
    private final StorageUnitQueryService storageUnitQueryService;
    private final Access access;
    private final AccountQueryService accountQueryService;
    private final NotificationService notificationService;
    private final NotificationTemplateCatalog templateCatalog;
    private final EmailSender emailSender;
    private final UnitTypeQueryService unitTypeQueryService;
    private final PolicyQueryService policyQueryService;

    public RentalRequestService(RentalRequestRepository repository, FacilityAccess facilityAccess,
                                StorageUnitQueryService storageUnitQueryService, Access access,
                                AccountQueryService accountQueryService,
                                NotificationService notificationService,
                                NotificationTemplateCatalog templateCatalog, EmailSender emailSender,
                                UnitTypeQueryService unitTypeQueryService,
                                PolicyQueryService policyQueryService) {
        this.repository = repository;
        this.facilityAccess = facilityAccess;
        this.storageUnitQueryService = storageUnitQueryService;
        this.access = access;
        this.accountQueryService = accountQueryService;
        this.notificationService = notificationService;
        this.templateCatalog = templateCatalog;
        this.emailSender = emailSender;
        this.unitTypeQueryService = unitTypeQueryService;
        this.policyQueryService = policyQueryService;
    }

    @Transactional
    public RentalRequestResponse create(CreateRentalRequestRequest request) {
        if (!facilityAccess.exists(request.facilityId())) {
            throw new FacilityNotFoundException();
        }
        if (!unitTypeQueryService.exists(request.unitTypeId())) {
            throw new UnitTypeNotFoundException();
        }
        if (!unitTypeQueryService.existsForFacility(request.facilityId(), request.unitTypeId())) {
            throw new RentalRequestValidationException(
                    "Loại khoang không được cung cấp tại cơ sở này");
        }
        LocalDate today = LocalDate.now(FACILITY_ZONE);
        if (request.startDate().isBefore(today)) {
            throw new RentalRequestValidationException("Ngày bắt đầu thuê không được ở quá khứ");
        }
        String normalizedEmail = request.customerEmail().strip().toLowerCase();
        String phoneDigits = request.customerPhone().replaceAll("\\D", "");
        if (phoneDigits.length() < 7 || phoneDigits.length() > 15) {
            throw new RentalRequestValidationException("Số điện thoại không đúng định dạng hỗ trợ");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(pendingExpiryDays() * 24L * 60L * 60L);
        RentalRequest saved = repository.save(new RentalRequest(request.facilityId(),
                request.customerName().strip(), normalizedEmail, request.customerPhone().strip(),
                request.unitTypeId(), request.startDate(), request.period(), expiresAt));
        return RentalRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public RentalRequestPageResponse queue(Long facilityId, RentalRequestStatus status, int page,
            int size) {
        if (!facilityAccess.exists(facilityId)) {
            throw new FacilityNotFoundException();
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<RentalRequest> result = status == null
                ? repository.findByFacilityId(facilityId, pageable)
                : repository.findByFacilityIdAndStatus(facilityId, status, pageable);
        return new RentalRequestPageResponse(
                result.getContent().stream().map(RentalRequestSummaryResponse::from).toList(), page,
                size, result.getTotalElements());
    }

    /**
     * FM duyệt yêu cầu (B1, spec draft.md §1.1) — dừng ở Approved, không tạo proposal/order (B2).
     *
     * <p>Guard {@code rental_request.approve} đặt trong service (thay vì controller) vì cần
     * {@code facilityId} từ entity mới guard được theo cơ sở — controller không có sẵn.
     * Thứ tự đúng spec + #126: 404 nếu request không tồn tại, 403 chéo cơ sở, 409 nếu request
     * không còn Pending, 409 nếu khoang mất Available, 409 nếu khoang đã gán cho request khác
     * của cùng email (giữ Pending, không noti).
     *
     * <p>Sau duyệt: email chưa có account nhận mail đăng ký theo template draft.md:156
     * (after-commit); email đã có account để B2 thông báo kèm proposal link.
     */
    @Transactional
    public ApproveRentalRequestResponse approve(Long requestId, Long unitId) {
        RentalRequest request = repository.findById(requestId)
                .orElseThrow(RentalRequestNotFoundException::new);
        access.can("rental_request.approve", request.getFacilityId());
        if (request.getStatus() != RentalRequestStatus.Pending) {
            throw new RentalRequestAlreadyHandledException();
        }
        if (unitId == null || !storageUnitQueryService.isAvailable(unitId)) {
            throw new StorageUnitUnavailableException();
        }
        if (repository.existsByNormalizedCustomerEmailAndUnitIdAndStatusIn(
                request.getNormalizedCustomerEmail(), unitId,
                List.of(RentalRequestStatus.Approved, RentalRequestStatus.Converted))) {
            throw new RentalRequestDuplicateUnitException();
        }
        Instant respondedAt = Instant.now();
        Instant expiresAt = respondedAt.plusSeconds(claimExpiryDays() * 24L * 60L * 60L);
        request.approve(unitId, respondedAt, expiresAt);
        sendApprovalEmail(request, expiresAt);
        return ApproveRentalRequestResponse.from(request);
    }

    /**
     * Mail duyệt cho nhánh email chưa có account (template draft.md:156, #126) — after-commit,
     * lỗi mail chỉ log, không rollback. Nhánh đã có account để B2 thông báo kèm proposal link.
     */
    private void sendApprovalEmail(RentalRequest request, Instant expiresAt) {
        try {
            accountQueryService.requireIdByEmail(request.getNormalizedCustomerEmail());
            return;
        } catch (AccountNotFoundException ex) {
            // Chua co account — gui mail dang ky ben duoi.
        }
        String deadline = VI_DATE.format(expiresAt.atZone(FACILITY_ZONE));
        String title = "Yêu cầu đặt kho của bạn đã được duyệt";
        String body = "Yêu cầu đặt kho của bạn đã được duyệt, nhưng hệ thống nhận thấy email này "
                + "chưa có tài khoản trên website, vui lòng đăng ký tại /register trước " + deadline
                + " và đăng nhập để xác nhận và đặt cọc.\n\nLưu ý: khoang chứa không được giữ "
                + "trong lúc chờ. Khoang được xác nhận chính thức cho khách hàng hoàn tất thanh "
                + "toán cọc đầu tiên (cọc trước giữ trước).";
        emailSender.sendHtml(request.getNormalizedCustomerEmail(), title,
                NotificationEmailLayout.wrap(title, body));
    }

    /**
     * FM từ chối yêu cầu (B1 phần 4, spec draft.md §1.1) — trạng thái cuối Rejected, không sang B2.
     *
     * <p>Guard {@code rental_request.reject} đặt trong service (giống approve) vì cần
     * {@code facilityId} từ entity. Thứ tự: 404 nếu request không tồn tại, 403 chéo cơ sở,
     * 409 nếu request không còn Pending, 400 nếu {@code OTHER} thiếu ghi chú.
     *
     * <p>Sau khi đổi trạng thái: gửi mail từ chối cho khách trong mọi trường hợp (after-commit,
     * lỗi mail chỉ log, không rollback); tạo thêm thông báo web chỉ khi email đã có account.
     */
    @Transactional
    public RejectRentalRequestResponse reject(Long requestId, RentalRequestRejectReason reason,
                                              String note) {
        RentalRequest request = repository.findById(requestId)
                .orElseThrow(RentalRequestNotFoundException::new);
        access.can("rental_request.reject", request.getFacilityId());
        if (request.getStatus() != RentalRequestStatus.Pending) {
            throw new RentalRequestAlreadyHandledException();
        }
        if (reason == null) {
            throw new RentalRequestValidationException("Lý do từ chối không được để trống");
        }
        String trimmedNote = note == null ? null : note.strip();
        if (reason == RentalRequestRejectReason.OTHER
                && (trimmedNote == null || trimmedNote.isEmpty())) {
            throw new RentalRequestValidationException("Ghi chú bắt buộc khi lý do là Khác");
        }
        request.reject(reason, trimmedNote, Instant.now());
        sendRejection(request, reason, trimmedNote);
        return RejectRentalRequestResponse.from(request);
    }

    private void sendRejection(RentalRequest request, RentalRequestRejectReason reason,
                               String note) {
        String reasonText = rejectReasonText(reason, note);
        Map<String, String> values = Map.of("reason", reasonText);
        Long accountId = null;
        try {
            accountId = accountQueryService
                    .requireIdByEmail(request.getNormalizedCustomerEmail());
        } catch (AccountNotFoundException ex) {
            accountId = null;
        }
        if (accountId != null) {
            notificationService.notify(accountId, request.getNormalizedCustomerEmail(),
                    NotificationRecipient.CUSTOMER, NotificationType.RENTAL_REQUEST_REJECTED,
                    values, null);
            return;
        }
        NotificationTemplateCatalog.Template template = templateCatalog
                .resolve(NotificationType.RENTAL_REQUEST_REJECTED, NotificationRecipient.CUSTOMER);
        emailSender.sendHtml(request.getNormalizedCustomerEmail(),
                template.renderTitle(values),
                NotificationEmailLayout.wrap(template.renderTitle(values),
                        template.renderBody(values)));
    }

    private static String rejectReasonText(RentalRequestRejectReason reason, String note) {
        String label = switch (reason) {
            case UNIT_UNAVAILABLE -> "Khoang không còn trống";
            case UNIT_MISMATCH -> "Khoang không phù hợp với nhu cầu";
            case CUSTOMER_REQUEST -> "Theo yêu cầu của khách";
            case OTHER -> "Lý do khác";
        };
        return note == null || note.isEmpty() ? label : label + " - " + note;
    }

    private long pendingExpiryDays() {
        return policyDays(PENDING_EXPIRY_KEY);
    }

    private long claimExpiryDays() {
        return policyDays(CLAIM_EXPIRY_KEY);
    }

    private long policyDays(String key) {
        try {
            return policyQueryService.valueOf(key).map(Long::parseLong).orElse(
                    EXPIRY_FALLBACK_DAYS);
        } catch (NumberFormatException ex) {
            return EXPIRY_FALLBACK_DAYS;
        }
    }
}
