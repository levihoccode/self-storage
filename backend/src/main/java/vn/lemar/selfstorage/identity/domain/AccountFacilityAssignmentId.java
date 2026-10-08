package vn.lemar.selfstorage.identity.domain;

import java.io.Serializable;
import java.util.Objects;

public class AccountFacilityAssignmentId implements Serializable {

    private Long accountId;
    private Long facilityId;

    public AccountFacilityAssignmentId() {
        // JPA
    }

    public AccountFacilityAssignmentId(Long accountId, Long facilityId) {
        this.accountId = accountId;
        this.facilityId = facilityId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccountFacilityAssignmentId that)) {
            return false;
        }
        return Objects.equals(accountId, that.accountId) && Objects.equals(facilityId, that.facilityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, facilityId);
    }
}