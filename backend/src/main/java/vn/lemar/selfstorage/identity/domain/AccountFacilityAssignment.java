package vn.lemar.selfstorage.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * facilityId la plain Long (khong @ManyToOne toi Facility entity) vi module
 * identity khai bao allowedDependencies rong trong package-info.java.
 */
@Entity
@Table(name = "account_facility_assignments")
@IdClass(AccountFacilityAssignmentId.class)
public class AccountFacilityAssignment {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Id
    @Column(name = "facility_id")
    private Long facilityId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    protected AccountFacilityAssignment() {
        // JPA
    }

    public AccountFacilityAssignment(Long accountId, Long facilityId) {
        this.accountId = accountId;
        this.facilityId = facilityId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getFacilityId() {
        return facilityId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}