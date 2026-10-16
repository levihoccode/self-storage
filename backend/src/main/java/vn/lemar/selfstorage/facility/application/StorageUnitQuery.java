package vn.lemar.selfstorage.facility.application;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.facility.domain.StorageUnit;
import vn.lemar.selfstorage.facility.repository.StorageUnitRepository;

/** Public API đọc khoang chứa cho module nghiệp vụ. */
@Service
public class StorageUnitQuery {

    private final StorageUnitRepository storageUnitRepository;

    public StorageUnitQuery(StorageUnitRepository storageUnitRepository) {
        this.storageUnitRepository = storageUnitRepository;
    }

    @Transactional(readOnly = true)
    public Optional<StorageUnitView> find(Long unitId) {
        return storageUnitRepository.findById(unitId).map(StorageUnitQuery::toView);
    }

    /**
     * Đọc khoang và giữ khoá row tới hết transaction của caller — dùng khi quyết định nghiệp vụ
     * dựa trên {@code status}. Bắt buộc gọi trong transaction có sẵn (propagation MANDATORY).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<StorageUnitView> lock(Long unitId) {
        return storageUnitRepository.findByIdForUpdate(unitId).map(StorageUnitQuery::toView);
    }

    private static StorageUnitView toView(StorageUnit unit) {
        return new StorageUnitView(unit.getId(), unit.getCode(), unit.getFacilityId(), unit.getUnitTypeId(),
                unit.getStatus().dbValue());
    }
}
