package vn.lemar.selfstorage.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestPageResponse;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;
import vn.lemar.selfstorage.facility.application.FacilityAccess;
import vn.lemar.selfstorage.pricing.application.PolicyQueryService;
import vn.lemar.selfstorage.pricing.application.UnitTypeQueryService;

@ExtendWith(MockitoExtension.class)
class RentalRequestQueueTest {

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
    void queueFiltersByFacilityAndStatusNewestFirst() {
        when(facilityAccess.exists(1L)).thenReturn(true);
        when(repository.findByFacilityIdAndStatus(eq(1L), eq(RentalRequestStatus.Pending),
                any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Pending, 0, 20);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(repository).findByFacilityIdAndStatus(eq(1L), eq(RentalRequestStatus.Pending),
                captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").getDirection().name())
                .isEqualTo("DESC");
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isZero();
    }

    @Test
    void queueWithoutStatusReturnsAllOfFacility() {
        when(facilityAccess.exists(1L)).thenReturn(true);
        when(repository.findByFacilityId(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        RentalRequestPageResponse page = service.queue(1L, null, 1, 10);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.total()).isZero();
    }
}
