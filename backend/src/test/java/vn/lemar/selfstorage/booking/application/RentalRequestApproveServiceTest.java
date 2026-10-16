package vn.lemar.selfstorage.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.lemar.selfstorage.booking.application.dto.ApproveRentalRequestResponse;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestAlreadyHandledException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestDuplicateUnitException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitUnavailableException;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;
import vn.lemar.selfstorage.facility.application.FacilityAccess;
import vn.lemar.selfstorage.facility.application.StorageUnitQueryService;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.identity.application.exception.ForbiddenException;
import vn.lemar.selfstorage.notification.EmailSender;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationTemplateCatalog;
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

    @InjectMocks
    private RentalRequestService service;

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

        ApproveRentalRequestResponse response = service.approve(1L, 10L);

        assertThat(response.status()).isEqualTo("Approved");
        assertThat(response.unitId()).isEqualTo(10L);
        assertThat(response.respondedAt()).isNotNull();
        assertThat(response.expiresAt())
                .isEqualTo(response.respondedAt().plusSeconds(7L * 24L * 60L * 60L));
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

        service.approve(1L, 10L);

        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(RentalRequestAlreadyHandledException.class)
                .hasMessage("Yêu cầu đã được xử lý bởi FM khác");
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
        when(repository.existsByNormalizedCustomerEmailAndUnitIdAndStatusIn(
                eq("khach@example.com"), eq(10L), any())).thenReturn(true);

        assertThatThrownBy(() -> service.approve(1L, 10L))
                .isInstanceOf(RentalRequestDuplicateUnitException.class)
                .hasMessageContaining("đã được gán cho yêu cầu khác của cùng khách hàng");
        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.Pending);
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

        ApproveRentalRequestResponse response = service.approve(1L, 10L);

        assertThat(response.status()).isEqualTo("Approved");
        verify(emailSender).sendHtml(eq("khach@example.com"), any(), any());
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
}
