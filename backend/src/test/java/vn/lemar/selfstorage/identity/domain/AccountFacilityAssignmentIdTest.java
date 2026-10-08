package vn.lemar.selfstorage.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountFacilityAssignmentIdTest {

    @Test
    void equalsAndHashCodeCoverAllBranches() {
        AccountFacilityAssignmentId id = new AccountFacilityAssignmentId(1L, 2L);

        assertThat(id).isEqualTo(id);
        assertThat(id).isEqualTo(new AccountFacilityAssignmentId(1L, 2L));
        assertThat(id.hashCode()).isEqualTo(new AccountFacilityAssignmentId(1L, 2L).hashCode());
        assertThat(id).isNotEqualTo(null);
        assertThat(id).isNotEqualTo("not an id");
        assertThat(id).isNotEqualTo(new AccountFacilityAssignmentId(9L, 2L));
        assertThat(id).isNotEqualTo(new AccountFacilityAssignmentId(1L, 9L));
    }
}