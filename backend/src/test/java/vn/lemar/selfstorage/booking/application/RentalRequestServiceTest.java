package vn.lemar.selfstorage.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.lemar.selfstorage.booking.application.dto.CreateRentalRequestRequest;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestResponse;
import vn.lemar.selfstorage.booking.application.exception.FacilityNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestValidationException;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;
import vn.lemar.selfstorage.facility.application.FacilityAccess;
import vn.lemar.selfstorage.pricing.application.PolicyQueryService;
import vn.lemar.selfstorage.pricing.application.UnitTypeQueryService;

@ExtendWith(MockitoExtension.class)
class RentalRequestServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private RentalRequestRepository repository;

    @Mock
    private FacilityAccess facilityAccess;

    @Mock
    private UnitTypeQueryService unitTypeQueryService;

    @Mock
    private PolicyQueryService policyQueryService;

    @InjectMocks
    private RentalRequestService service;

    @Test
    void createNormalizesEmailAndSetsExpiry() {
        when(facilityAccess.exists(1L)).thenReturn(true);
        when(unitTypeQueryService.exists(2L)).thenReturn(true);
        when(unitTypeQueryService.existsForFacility(1L, 2L)).thenReturn(true);
        when(policyQueryService.valueOf("request.pending_expiry_days"))
                .thenReturn(Optional.of("7"));
        when(repository.save(any(RentalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RentalRequestResponse response = service.create(validRequest());

        ArgumentCaptor<RentalRequest> captor = ArgumentCaptor.forClass(RentalRequest.class);
        verify(repository).save(captor.capture());
        RentalRequest saved = captor.getValue();
        assertThat(saved.getNormalizedCustomerEmail()).isEqualTo("khach@example.com");
        assertThat(saved.getStatus().name()).isEqualTo("Pending");
        assertThat(saved.getExpiresAt()).isNotNull();
        assertThat(response.status()).isEqualTo("Pending");
    }

    @Test
    void createRejectsPastStartDate() {
        when(facilityAccess.exists(1L)).thenReturn(true);
        when(unitTypeQueryService.exists(2L)).thenReturn(true);
        when(unitTypeQueryService.existsForFacility(1L, 2L)).thenReturn(true);

        CreateRentalRequestRequest request = new CreateRentalRequestRequest(1L, "Nguyen Van A",
                "khach@example.com", "0901234567", 2L, LocalDate.now(ZONE).minusDays(1), 6);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RentalRequestValidationException.class);
    }

    @Test
    void createRejectsUnknownFacility() {
        when(facilityAccess.exists(99L)).thenReturn(false);

        CreateRentalRequestRequest request = new CreateRentalRequestRequest(99L, "Nguyen Van A",
                "khach@example.com", "0901234567", 2L, LocalDate.now(ZONE).plusDays(1), 6);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(FacilityNotFoundException.class);
    }

    @Test
    void createRejectsUnknownUnitTypeReturns400() {
        when(facilityAccess.exists(1L)).thenReturn(true);
        when(unitTypeQueryService.exists(2L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(RentalRequestValidationException.class)
                .hasMessage("Loại khoang không tồn tại");
    }

    @Test
    void createRejectsUnitTypeNotProvidedByFacility() {
        when(facilityAccess.exists(1L)).thenReturn(true);
        when(unitTypeQueryService.exists(2L)).thenReturn(true);
        when(unitTypeQueryService.existsForFacility(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(RentalRequestValidationException.class)
                .hasMessage("Loại khoang không được cung cấp tại cơ sở này");
    }

    private CreateRentalRequestRequest validRequest() {
        return new CreateRentalRequestRequest(1L, "Nguyen Van A", "  Khach@Example.com ",
                "0901234567", 2L, LocalDate.now(ZONE).plusDays(1), 6);
    }
}
