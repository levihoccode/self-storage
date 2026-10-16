package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Khoang chứa. Flow 1.4 đọc trạng thái để quyết cho thanh toán hay không, và đẩy sang
 * {@code Reserved} khi cọc thành công — chính trạng thái này giữ chỗ cho khách.
 */
@Entity
@Table(name = "storage_units")
public class StorageUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Convert(converter = StorageUnitStatusConverter.class)
    @Column(nullable = false, length = 20)
    private StorageUnitStatus status = StorageUnitStatus.AVAILABLE;

    protected StorageUnit() {
        // JPA
    }

    public boolean isAvailable() {
        return status == StorageUnitStatus.AVAILABLE;
    }

    /** Giữ chỗ sau khi khách cọc thành công. */
    public void reserve() {
        this.status = StorageUnitStatus.RESERVED;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Long getFacilityId() {
        return facilityId;
    }

    public StorageUnitStatus getStatus() {
        return status;
    }
}
