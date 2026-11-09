package vn.lemar.selfstorage.booking.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReproposalPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);
    private static final Instant EARLIER = NOW.minusSeconds(60);

    @Test
    void allowsReproposeForPendingOrderWithRejectedProposal() {
        assertThat(ReproposalPolicy.canRepropose("Pending", LATER, false, "Rejected", 0, NOW)).isTrue();
    }

    @Test
    void allowsReproposeWhenOrderHasNoProposalYet() {
        assertThat(ReproposalPolicy.canRepropose("Pending", LATER, false, null, 0, NOW)).isTrue();
    }

    @Test
    void allowsReproposeForCanceledLatestProposal() {
        assertThat(ReproposalPolicy.canRepropose("Pending", LATER, false, "Canceled", 0, NOW)).isTrue();
    }

    @Test
    void allowsReproposeAfterDepositWhenFlagIsSet() {
        assertThat(ReproposalPolicy.canRepropose("Deposited", LATER, true, "Rejected", 0, NOW)).isTrue();
    }

    @Test
    void deniesReproposeAfterDepositWithoutFlag() {
        assertThat(ReproposalPolicy.canRepropose("Deposited", LATER, false, "Rejected", 0, NOW)).isFalse();
    }

    @Test
    void deniesReproposeWhenRequestHasNoOrder() {
        assertThat(ReproposalPolicy.canRepropose(null, null, false, null, 0, NOW)).isFalse();
    }

    @Test
    void deniesReproposeWhenOrderExpired() {
        // `POST /api/rental-orders/{id}/proposals` trả 409 với đơn quá hạn.
        assertThat(ReproposalPolicy.canRepropose("Pending", EARLIER, false, "Rejected", 0, NOW)).isFalse();
    }

    @Test
    void allowsReproposeAtTheExactExpiryInstant() {
        assertThat(ReproposalPolicy.canRepropose("Pending", NOW, false, "Rejected", 0, NOW)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "Canceled,   Pending,  0",
            "Done,       Rejected, 0",
            "Pending,    Agreed,   0",
            "Pending,    Pending,  1",
    })
    void deniesReproposeForBlockingStates(String orderStatus, String lastProposalStatus, long pendingCount) {
        assertThat(ReproposalPolicy.canRepropose(orderStatus, LATER, true, lastProposalStatus, pendingCount, NOW))
                .isFalse();
    }
}
