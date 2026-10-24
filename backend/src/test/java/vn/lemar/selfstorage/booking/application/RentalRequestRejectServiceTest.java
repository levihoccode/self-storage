package vn.lemar.selfstorage.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
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
import vn.lemar.selfstorage.booking.application.dto.RejectRentalRequestResponse;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestAlreadyHandledException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestValidationException;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.domain.RentalRequestRejectReason;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;
import vn.lemar.selfstorage.facility.application.FacilityAccess;
import vn.lemar.selfstorage.facility.application.StorageUnitQuery;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.identity.application.exception.ForbiddenException;
import vn.lemar.selfstorage.notification.EmailSender;
import vn.lemar.selfstorage.notification.application.NotificationRecipient;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationTemplateCatalog;
import vn.lemar.selfstorage.notification.application.NotificationType;
import vn.lemar.selfstorage.pricing.application.PricingQuery;
import vn.lemar.selfstorage.pricing.application.UnitTypeQueryService;

/**
 * Test tuần tự cho reject (B1 phần 4).
 */
@ExtendWith(MockitoExtension.class)
class RentalRequestRejectServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private RentalRequestRepository repository;

    @Mock
    private FacilityAccess facilityAccess;

    @Mock
    private StorageUnitQuery storageUnitQuery;

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
    private PricingQuery pricingQuery;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private CurrentAccountProvider currentAccountProvider;

    @InjectMocks
    private RentalRequestService service;

    @Test
    void rejectWithAccountSendsWebNotification() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.reject"), eq(5L));
        when(repository.rejectIfPending(eq(1L),
                eq(RentalRequestRejectReason.UNIT_UNAVAILABLE), any(), any(),
                eq(RentalRequestStatus.Pending), eq(RentalRequestStatus.Rejected))).thenReturn(1);
        when(accountQueryService.findIdByEmail("khach@example.com")).thenReturn(Optional.of(42L));
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(77L);

        RejectRentalRequestResponse response = service.reject(1L,
                RentalRequestRejectReason.UNIT_UNAVAILABLE, null);

        assertThat(response.status()).isEqualTo("Rejected");
        assertThat(response.rejectReason()).isEqualTo("UNIT_UNAVAILABLE");
        assertThat(response.respondedAt()).isNotNull();
        verify(notificationService).notify(eq(42L), eq("khach@example.com"),
                eq(NotificationRecipient.CUSTOMER),
                eq(NotificationType.RENTAL_REQUEST_REJECTED), any(Map.class), eq(null));
        verify(emailSender, never()).sendHtml(any(), any(), any());

        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.RENTAL_REQUEST_REJECTED);
        assertThat(audit.getValue().entityType()).isEqualTo(AuditEntityType.RENTAL_REQUEST);
        assertThat(audit.getValue().entityId()).isEqualTo("1");
        assertThat(audit.getValue().actorAccountId()).isEqualTo(77L);
        assertThat(audit.getValue().newValue()).asInstanceOf(MAP)
                .containsEntry("status", "Rejected")
                .containsEntry("rejectReason", "UNIT_UNAVAILABLE");
    }

    @Test
    void rejectWithoutAccountSendsEmailOnly() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.reject"), eq(5L));
        when(repository.rejectIfPending(eq(1L),
                eq(RentalRequestRejectReason.CUSTOMER_REQUEST), any(), any(),
                eq(RentalRequestStatus.Pending), eq(RentalRequestStatus.Rejected))).thenReturn(1);
        when(accountQueryService.findIdByEmail("khach@example.com"))
                .thenReturn(Optional.empty());
        when(templateCatalog.resolve(NotificationType.RENTAL_REQUEST_REJECTED,
                NotificationRecipient.CUSTOMER)).thenReturn(
                        new NotificationTemplateCatalog.Template("T {{reason}}", "B {{reason}}"));
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(77L);

        RejectRentalRequestResponse response = service.reject(1L,
                RentalRequestRejectReason.CUSTOMER_REQUEST, null);

        assertThat(response.status()).isEqualTo("Rejected");
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
        verify(emailSender).sendHtml(eq("khach@example.com"), any(), any());
        verify(auditRecorder).record(any(AuditEntry.class));
    }

    @Test
    void rejectTwiceSecondReturns409() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.reject"), eq(5L));
        when(repository.rejectIfPending(eq(1L), eq(RentalRequestRejectReason.UNIT_MISMATCH),
                eq("note"), any(), eq(RentalRequestStatus.Pending),
                eq(RentalRequestStatus.Rejected))).thenReturn(1, 0); // lần 2: 0 row — đã xử lý
        when(accountQueryService.findIdByEmail("khach@example.com")).thenReturn(Optional.of(42L));
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(77L);

        service.reject(1L, RentalRequestRejectReason.UNIT_MISMATCH, "note");

        assertThatThrownBy(
                () -> service.reject(1L, RentalRequestRejectReason.UNIT_MISMATCH, "note"))
                .isInstanceOf(RentalRequestAlreadyHandledException.class)
                .hasMessage("Yêu cầu đã được xử lý bởi FM khác");
        verify(auditRecorder).record(any(AuditEntry.class)); // chỉ lần từ chối đầu ghi audit
    }

    @Test
    void rejectUnknownRequestReturns404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.reject(99L, RentalRequestRejectReason.UNIT_UNAVAILABLE, null))
                .isInstanceOf(RentalRequestNotFoundException.class);
    }

    @Test
    void rejectCrossFacilityReturns403() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doThrow(new ForbiddenException("Bạn không được gán vào cơ sở này"))
                .when(access).can(eq("rental_request.reject"), anyLong());

        assertThatThrownBy(
                () -> service.reject(1L, RentalRequestRejectReason.UNIT_UNAVAILABLE, null))
                .isInstanceOf(ForbiddenException.class);
        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.Pending);
    }

    @Test
    void rejectOtherWithoutNoteReturns400() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.reject"), eq(5L));

        assertThatThrownBy(() -> service.reject(1L, RentalRequestRejectReason.OTHER, "  "))
                .isInstanceOf(RentalRequestValidationException.class)
                .hasMessage("Ghi chú bắt buộc khi lý do là Khác");
        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.Pending);
    }

    @Test
    void rejectOtherWithNoteOk() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.reject"), eq(5L));
        when(repository.rejectIfPending(eq(1L), eq(RentalRequestRejectReason.OTHER),
                eq("Khach doi y"), any(), eq(RentalRequestStatus.Pending),
                eq(RentalRequestStatus.Rejected))).thenReturn(1);
        when(accountQueryService.findIdByEmail("khach@example.com")).thenReturn(Optional.of(42L));
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(77L);

        RejectRentalRequestResponse response = service.reject(1L,
                RentalRequestRejectReason.OTHER, "Khach doi y");

        assertThat(response.status()).isEqualTo("Rejected");
        verify(repository).rejectIfPending(eq(1L), eq(RentalRequestRejectReason.OTHER),
                eq("Khach doi y"), any(), eq(RentalRequestStatus.Pending),
                eq(RentalRequestStatus.Rejected));

        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().newValue()).asInstanceOf(MAP)
                .containsEntry("rejectReason", "OTHER")
                .containsEntry("rejectNote", "Khach doi y");
    }

    @Test
    void rejectNullReasonReturns400() {
        RentalRequest request = pendingRequest();
        when(repository.findById(1L)).thenReturn(Optional.of(request));
        doNothing().when(access).can(eq("rental_request.reject"), eq(5L));

        assertThatThrownBy(() -> service.reject(1L, null, null))
                .isInstanceOf(RentalRequestValidationException.class);
        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.Pending);
    }

    private RentalRequest pendingRequest() {
        return new RentalRequest(5L, "Nguyen Van A", "khach@example.com", "0901234567", 2L,
                LocalDate.now(ZONE).plusDays(1), 6, Instant.now().plusSeconds(7L * 24L * 3600L));
    }
}
