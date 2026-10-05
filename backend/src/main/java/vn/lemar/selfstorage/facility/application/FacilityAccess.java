package vn.lemar.selfstorage.facility.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.facility.repository.FacilityAccessRepository;

/** Public API for checking facility ownership by its assigned facility manager. */
@Service
public class FacilityAccess {

    private final FacilityAccessRepository facilityAccessRepository;

    public FacilityAccess(FacilityAccessRepository facilityAccessRepository) {
        this.facilityAccessRepository = facilityAccessRepository;
    }

    @Transactional(readOnly = true)
    public boolean isManagedBy(Long accountId, Long facilityId) {
        return facilityAccessRepository.isManagedBy(accountId, facilityId);
    }
}
