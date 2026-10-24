package vn.lemar.selfstorage.facility.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.facility.domain.StorageUnit;
import vn.lemar.selfstorage.facility.repository.StorageUnitRepository;

/** Public API đổi trạng thái khoang cho module nghiệp vụ — luôn khoá row theo {@code unit_id}. */
@Service
public class StorageUnitService {

    private final StorageUnitRepository storageUnitRepository;

    public StorageUnitService(StorageUnitRepository storageUnitRepository) {
        this.storageUnitRepository = storageUnitRepository;
    }

    /**
     * Cancellation Cascade: giải phóng khoang {@code Reserved -> Available}. Khoang ở trạng thái khác (chưa
     * cọc nên vẫn {@code Available}, hoặc {@code Maintenance}) giữ nguyên. Chạy trong transaction của caller.
     *
     * @return {@code true} nếu trạng thái khoang thực sự đổi — caller ghi AuditLog cho khoang
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean releaseIfReserved(Long unitId) {
        return storageUnitRepository.findByIdForUpdate(unitId)
                .map(StorageUnit::releaseIfReserved)
                .orElse(false);
    }
}
