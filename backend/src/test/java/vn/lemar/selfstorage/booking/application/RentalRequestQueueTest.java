package vn.lemar.selfstorage.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestPageResponse;
import vn.lemar.selfstorage.booking.domain.RentalRequest;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;
import vn.lemar.selfstorage.booking.repository.BookingReadRepository;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;
import vn.lemar.selfstorage.facility.application.FacilityAccess;
import vn.lemar.selfstorage.pricing.application.PricingQuery;
import vn.lemar.selfstorage.pricing.application.UnitTypeQueryService;

@ExtendWith(MockitoExtension.class)
class RentalRequestQueueTest {

    @Mock
    private RentalRequestRepository repository;

    @Mock
    private BookingReadRepository bookingReadRepository;

    @Mock
    private FacilityAccess facilityAccess;

    @Mock
    private UnitTypeQueryService unitTypeQueryService;

    @Mock
    private PricingQuery pricingQuery;

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

    @Test
    void marksReproposeWhenLastProposalRejected() {
        stubQueue(RentalRequestStatus.Converted, enrichment("Pending", false, future(), "Rejected", 1, 0));

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Converted, 0, 20);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.needsRepropose()).isTrue();
            assertThat(item.orderId()).isEqualTo(10L);
            assertThat(item.rejectionCount()).isEqualTo(1);
            assertThat(item.maxRejectionCount()).isEqualTo(3);
            assertThat(item.lastProposal().status()).isEqualTo("Rejected");
        });
    }

    @Test
    void doesNotMarkWhenProposalPending() {
        stubQueue(RentalRequestStatus.Converted, enrichment("Pending", false, future(), "Pending", 0, 1));

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Converted, 0, 20);

        assertThat(page.items().get(0).needsRepropose()).isFalse();
    }

    @Test
    void doesNotMarkWhenProposalAgreed() {
        stubQueue(RentalRequestStatus.Converted, enrichment("Pending", false, future(), "Agreed", 0, 0));

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Converted, 0, 20);

        assertThat(page.items().get(0).needsRepropose()).isFalse();
    }

    @Test
    void doesNotMarkWhenOrderClosed() {
        stubQueue(RentalRequestStatus.Converted, enrichment("Canceled", false, future(), "Rejected", 1, 0));

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Converted, 0, 20);

        assertThat(page.items().get(0).needsRepropose()).isFalse();
    }

    @Test
    void doesNotMarkForPendingRequestWithoutOrder() {
        RentalRequest request = request(RentalRequestStatus.Pending);
        when(facilityAccess.exists(1L)).thenReturn(true);
        when(repository.findByFacilityIdAndStatus(eq(1L), eq(RentalRequestStatus.Pending),
                any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(request)));
        when(bookingReadRepository.findQueueEnrichments(List.of(1L))).thenReturn(Map.of());
        when(pricingQuery.intPolicy(ProposalService.POLICY_MAX_REJECTION)).thenReturn(3);

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Pending, 0, 20);

        assertThat(page.items().get(0).needsRepropose()).isFalse();
    }

    @Test
    void marksReproposeWhenNeedsReproposalFlagIsSet() {
        stubQueue(RentalRequestStatus.Converted, enrichment("Deposited", true, future(), null, 0, 0));

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Converted, 0, 20);

        assertThat(page.items().get(0).needsRepropose()).isTrue();
    }

    @Test
    void doesNotMarkForApprovedAndExpiredRequestWithoutOrder() {
        RentalRequest approved = request(RentalRequestStatus.Approved);
        RentalRequest expired = request(RentalRequestStatus.Expired);
        when(facilityAccess.exists(1L)).thenReturn(true);
        when(repository.findByFacilityId(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(approved, expired)));
        when(bookingReadRepository.findQueueEnrichments(List.of(1L, 1L))).thenReturn(Map.of());
        when(pricingQuery.intPolicy(ProposalService.POLICY_MAX_REJECTION)).thenReturn(3);

        RentalRequestPageResponse page = service.queue(1L, null, 0, 20);

        assertThat(page.items()).extracting(item -> item.needsRepropose()).containsOnly(false);
    }

    @Test
    void doesNotMarkWhenOrderExpired() {
        // `POST /api/rental-orders/{id}/proposals` trả 409 khi đơn quá expires_at — queue không
        // được mời FM đề xuất lại ở trạng thái đó.
        stubQueue(RentalRequestStatus.Converted,
                enrichment("Pending", false, Instant.now().minusSeconds(3600), "Rejected", 1, 0));

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Converted, 0, 20);

        assertThat(page.items().get(0).needsRepropose()).isFalse();
    }

    @Test
    void doesNotMarkWhenFlagSetButProposalPending() {
        stubQueue(RentalRequestStatus.Converted, enrichment("Deposited", true, future(), "Pending", 0, 1));

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Converted, 0, 20);

        assertThat(page.items().get(0).needsRepropose()).isFalse();
    }

    @Test
    void marksReproposeWhenLastProposalCanceled() {
        stubQueue(RentalRequestStatus.Converted, enrichment("Pending", false, future(), "Canceled", 1, 0));

        RentalRequestPageResponse page = service.queue(1L, RentalRequestStatus.Converted, 0, 20);

        assertThat(page.items().get(0).needsRepropose()).isTrue();
    }

    private void stubQueue(RentalRequestStatus status, BookingReadRepository.QueueEnrichment enrichment) {
        RentalRequest request = request(status);
        when(facilityAccess.exists(1L)).thenReturn(true);
        when(repository.findByFacilityIdAndStatus(eq(1L), eq(status), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(request)));
        when(bookingReadRepository.findQueueEnrichments(List.of(1L))).thenReturn(Map.of(1L, enrichment));
        when(pricingQuery.intPolicy(ProposalService.POLICY_MAX_REJECTION)).thenReturn(3);
    }

    private static Instant future() {
        return Instant.now().plusSeconds(3600);
    }

    private static BookingReadRepository.QueueEnrichment enrichment(String orderStatus, boolean needsReproposal,
            Instant orderExpiresAt, String lastProposalStatus, long rejectionCount, long pendingCount) {
        boolean hasProposal = lastProposalStatus != null;
        return new BookingReadRepository.QueueEnrichment(1L, 10L, orderStatus, needsReproposal, orderExpiresAt,
                hasProposal ? 5L : null, hasProposal ? "A-01" : null, lastProposalStatus,
                hasProposal ? "Không thích khoang này" : null, hasProposal ? Instant.now() : null,
                rejectionCount, pendingCount);
    }

    private static RentalRequest request(RentalRequestStatus status) {
        RentalRequest request = mock(RentalRequest.class);
        when(request.getId()).thenReturn(1L);
        when(request.getCustomerName()).thenReturn("Khách");
        when(request.getNormalizedCustomerEmail()).thenReturn("khach@example.com");
        when(request.getCustomerPhone()).thenReturn("0900000000");
        when(request.getUnitTypeId()).thenReturn(1L);
        when(request.getStartDate()).thenReturn(LocalDate.of(2026, 11, 1));
        when(request.getPeriod()).thenReturn(6);
        when(request.getStatus()).thenReturn(status);
        when(request.getCreatedAt()).thenReturn(Instant.now());
        when(request.getExpiresAt()).thenReturn(Instant.now());
        return request;
    }
}
