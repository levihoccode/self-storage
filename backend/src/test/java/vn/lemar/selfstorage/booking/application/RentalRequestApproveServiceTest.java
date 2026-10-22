package vn.lemar.selfstorage.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.booking.application.dto.ApproveRentalRequestResponse;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestAlreadyHandledException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestDuplicateUnitException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestExpiredException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitUnavailableException;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.domain.RentalRequestRejectReason;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;
import vn.lemar.selfstorage.booking.repository.ConversionGuardRepository;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;
import vn.lemar.selfstorage.facility.application.FacilityAccess;
import vn.lemar.selfstorage.facility.application.StorageUnitQueryService;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.identity.application.exception.ForbiddenException;
import vn.lemar.selfstorage.notification.EmailSender;
import vn.lemar.selfstorage.notification.application.NotificationRecipient;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationTemplateCatalog;
import vn.lemar.selfstorage.notification.application.NotificationType;
import vn.lemar.selfstorage.pricing.application.PolicyQueryService;
import vn.lemar.selfstorage.pricing.application.UnitTypeQueryService;

/**
 * Test tuần tự cho approve (B1 phần 3) — song song thật để khi có Docker/Postgres.
 */
@ExtendWith(MockitoExtension.class)
class RentalRequestApproveServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private RentalRequestRepository repository;

    @Mock
    private FacilityAccess facilityAccess;

    @Mock
    private StorageUnitQueryService storageUnitQueryService;

    @Mock
    private Access access;

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationTemplateCatalog templateCatalog;

    @Mock
    private EmailSender emailSender;

    @Mock
    private UnitTypeQueryService unitTypeQueryService;

    @Mock
    private PolicyQueryService policyQueryService;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private CurrentAccountProvider currentAccountProvider;

    // B2: approve gọi convert trong cùng transaction — collaborator mới của RentalRequestService.
    @Mock
    private RentalRequestConversionService conversionService;

    @Mock
    private ConversionGuardRepository conversionGuard;

    @InjectMocks
    private RentalRequestService service;

    @BeforeEach
    void defaultConversionResult() {
        // Mặc định convert thành công; test cần outcome khác thì stub lại. lenient vì nhiều test
        // fail trước khi tới bước convert.
        lenient().when(conversionService.convertOnApproval(anyLong()))
                .thenReturn(new ConversionResult(1L, ConversionResult.Outcome.CONVERTED, 1L, 1L));
    }

    @Test
    void approveConvertsRightAfterApprovalAndNeverOnFailedApproval() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(storageUnitQueryService.findUnitInfo(10L))
                .thenReturn(Optional.of(new StorageUnitQueryService.UnitInfo(5L, "Available")));
        when(policyQueryService.valueOf("account.claim_ttl_days")).thenReturn(Optional.of("7"));
        when(repository.approveIfPending(eq(1L), eq(10L), any(), any(),
                eq(RentalRequestStatus.Pending), eq(RentalRequestStatus.Approved))).thenReturn(1, 0);
        when(accountQueryService.findIdByEmail("khach@example.com")).thenReturn(Optional.of(42L));

        service.approve(1L, 10L);
        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(RentalRequestAlreadyHandledException.class);

        verify(conversionService).convertOnApproval(1L); // đúng 1 lần — lần duyệt thất bại không convert
    }

    @Test
    void approvePendingSetsApprovedAndExpiry() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(storageUnitQueryService.findUnitInfo(10L))
                .thenReturn(Optional.of(new StorageUnitQueryService.UnitInfo(5L, "Available")));
        when(policyQueryService.valueOf("account.claim_ttl_days"))
                .thenReturn(Optional.of("7"));
        when(repository.approveIfPending(eq(1L), eq(10L), any(), any(),
                eq(RentalRequestStatus.Pending), eq(RentalRequestStatus.Approved))).thenReturn(1);
        when(accountQueryService.findIdByEmail("khach@example.com")).thenReturn(Optional.of(42L));
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(77L);

        ApproveRentalRequestResponse response = service.approve(1L, 10L);

        assertThat(response.status()).isEqualTo("Approved");
        assertThat(response.unitId()).isEqualTo(10L);
        assertThat(response.respondedAt()).isNotNull();
        assertThat(response.expiresAt())
                .isEqualTo(response.respondedAt().plusSeconds(7L * 24L * 60L * 60L));

        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.RENTAL_REQUEST_APPROVED);
        assertThat(audit.getValue().entityType()).isEqualTo(AuditEntityType.RENTAL_REQUEST);
        assertThat(audit.getValue().entityId()).isEqualTo("1");
        assertThat(audit.getValue().actorAccountId()).isEqualTo(77L);
        assertThat(audit.getValue().newValue()).asInstanceOf(MAP)
                .containsEntry("status", "Approved")
                .containsEntry("unitId", 10L);
    }

    @Test
    void approveTwiceSecondReturns409() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(storageUnitQueryService.findUnitInfo(10L))
                .thenReturn(Optional.of(new StorageUnitQueryService.UnitInfo(5L, "Available")));
        when(policyQueryService.valueOf("account.claim_ttl_days"))
                .thenReturn(Optional.of("7"));
        when(repository.approveIfPending(eq(1L), eq(10L), any(), any(),
                eq(RentalRequestStatus.Pending), eq(RentalRequestStatus.Approved)))
                .thenReturn(1, 0); // lần 2: DB đã Approved — conditional update trả 0 row
        when(accountQueryService.findIdByEmail("khach@example.com")).thenReturn(Optional.of(42L));
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(77L);

        service.approve(1L, 10L);

        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(RentalRequestAlreadyHandledException.class)
                .hasMessage("Yêu cầu đã được xử lý bởi FM khác");
        verify(auditRecorder).record(any(AuditEntry.class)); // chỉ lần duyệt đầu ghi audit
    }

    @Test
    void approveUnavailableUnitKeepsPending() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(storageUnitQueryService.findUnitInfo(11L))
                .thenReturn(Optional.of(new StorageUnitQueryService.UnitInfo(5L, "Maintenance")));

        assertThatThrownBy(() -> service.approve(1L, 11L))
                .isInstanceOf(StorageUnitUnavailableException.class)
                .hasMessage("Khoang đã không còn khả dụng, vui lòng chọn khoang khác");
        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.Pending);
    }

    @Test
    void approveExpiredRequestReturns409() {
        RentalRequest request = expiredPendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));

        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(RentalRequestExpiredException.class)
                .hasMessage("Yêu cầu đã quá hạn xử lý");
        verify(repository, never()).approveIfPending(any(), any(), any(), any(), any(), any());
        verify(auditRecorder, never()).record(any(AuditEntry.class));
    }

    @Test
    void approveUnknownUnitReturns404() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(storageUnitQueryService.findUnitInfo(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(StorageUnitNotFoundException.class)
                .hasMessage("Không tìm thấy khoang");
        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.Pending);
    }

    @Test
    void approveUnitFromOtherFacilityReturns404() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(storageUnitQueryService.findUnitInfo(10L))
                .thenReturn(Optional.of(new StorageUnitQueryService.UnitInfo(7L, "Available")));

        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(StorageUnitNotFoundException.class)
                .hasMessage("Không tìm thấy khoang");
        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.Pending);
    }

    @Test
    void approveDuplicateUnitForSameEmailReturns409() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(storageUnitQueryService.findUnitInfo(10L))
                .thenReturn(Optional.of(new StorageUnitQueryService.UnitInfo(5L, "Available")));
        when(conversionGuard.hasActiveConversionForEmail("khach@example.com", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(RentalRequestDuplicateUnitException.class)
                .hasMessageContaining("đã được gán cho yêu cầu khác của cùng khách hàng");
        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.Pending);
    }

    @Test
    void approveBannedCustomerRejectsRequestAndSendsEmail() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(accountQueryService.isBanned("khach@example.com")).thenReturn(true);
        when(repository.rejectIfPending(eq(1L), eq(RentalRequestRejectReason.OTHER),
                eq("Tài khoản khách đã bị chặn"), any(), eq(RentalRequestStatus.Pending),
                eq(RentalRequestStatus.Rejected))).thenReturn(1);
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(77L);
        when(templateCatalog.resolve(NotificationType.RENTAL_REQUEST_REJECTED_BANNED,
                NotificationRecipient.CUSTOMER)).thenReturn(new NotificationTemplateCatalog.Template(
                        "Yêu cầu thuê kho bị từ chối",
                        "Tài khoản của bạn hiện đang bị chặn, nên yêu cầu đặt kho của bạn đã bị từ chối."));

        ApproveRentalRequestResponse response = service.approve(1L, 10L);

        assertThat(response.status()).isEqualTo("Rejected");
        assertThat(response.unitId()).isNull();
        assertThat(response.expiresAt()).isNull();
        verify(repository).rejectIfPending(eq(1L), eq(RentalRequestRejectReason.OTHER),
                eq("Tài khoản khách đã bị chặn"), any(), eq(RentalRequestStatus.Pending),
                eq(RentalRequestStatus.Rejected));
        verify(emailSender).sendHtml(eq("khach@example.com"), eq("Yêu cầu thuê kho bị từ chối"),
                contains("bị chặn"));
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.RENTAL_REQUEST_REJECTED);
        verify(storageUnitQueryService, never()).findUnitInfo(anyLong());
        verify(conversionService, never()).convertOnApproval(anyLong());
    }

    @Test
    void approveThrows409WhenConversionDetectsDuplicate() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(storageUnitQueryService.findUnitInfo(10L))
                .thenReturn(Optional.of(new StorageUnitQueryService.UnitInfo(5L, "Available")));
        when(policyQueryService.valueOf("account.claim_ttl_days")).thenReturn(Optional.of("7"));
        when(repository.approveIfPending(eq(1L), eq(10L), any(), any(),
                eq(RentalRequestStatus.Pending), eq(RentalRequestStatus.Approved))).thenReturn(1);
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(77L);
        when(conversionService.convertOnApproval(1L))
                .thenReturn(new ConversionResult(1L, ConversionResult.Outcome.DUPLICATE_UNIT, null, null));

        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(RentalRequestDuplicateUnitException.class)
                .hasMessageContaining("đã được gán cho yêu cầu khác của cùng khách hàng");
        verify(conversionGuard).lockCustomerEmail("khach@example.com");
    }

    @Test
    void approveWithoutAccountSendsRegisterEmail() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.approve"), eq(5L));
        when(storageUnitQueryService.findUnitInfo(10L))
                .thenReturn(Optional.of(new StorageUnitQueryService.UnitInfo(5L, "Available")));
        when(policyQueryService.valueOf("account.claim_ttl_days"))
                .thenReturn(Optional.of("7"));
        when(repository.approveIfPending(eq(1L), eq(10L), any(), any(),
                eq(RentalRequestStatus.Pending), eq(RentalRequestStatus.Approved))).thenReturn(1);
        when(accountQueryService.findIdByEmail("khach@example.com"))
                .thenReturn(Optional.empty());
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(77L);

        ApproveRentalRequestResponse response = service.approve(1L, 10L);

        assertThat(response.status()).isEqualTo("Approved");
        verify(emailSender).sendHtml(eq("khach@example.com"), any(), any());
        verify(auditRecorder).record(any(AuditEntry.class));
    }

    @Test
    void approveUnknownRequestReturns404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L, 10L))
                .isInstanceOf(RentalRequestNotFoundException.class);
    }

    @Test
    void approveCrossFacilityReturns403() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doThrow(new ForbiddenException("Bạn không được gán vào cơ sở này"))
                .when(access).can(eq("rental_request.approve"), anyLong());

        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(ForbiddenException.class);
        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.Pending);
    }

    private RentalRequest pendingRequest() {
        return new RentalRequest(5L, "Nguyen Van A", "khach@example.com", "0901234567", 2L,
                LocalDate.now(ZONE).plusDays(1), 6, Instant.now().plusSeconds(7L * 24L * 3600L));
    }

    private RentalRequest expiredPendingRequest() {
        return new RentalRequest(5L, "Nguyen Van A", "khach@example.com", "0901234567", 2L,
                LocalDate.now(ZONE).plusDays(1), 6, Instant.now().minusSeconds(3600L));
    }
}
