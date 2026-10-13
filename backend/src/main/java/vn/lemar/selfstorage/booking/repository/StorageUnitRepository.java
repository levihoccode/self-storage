package vn.lemar.selfstorage.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.booking.domain.StorageUnit;

public interface StorageUnitRepository extends JpaRepository<StorageUnit, Long> {
}
