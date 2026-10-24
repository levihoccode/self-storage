package vn.lemar.selfstorage.booking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversionGuardRepositoryTest {

    @Test
    void closedStatusesFollowTheOrderStatusEnum() {
        assertThat(ConversionGuardRepository.closedStatuses())
                .containsExactlyInAnyOrder("Canceled", "Done");
    }
}
