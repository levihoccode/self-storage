package vn.lemar.selfstorage.facility.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Khoang chứa — chỉ map các cột cần để đọc trạng thái, phạm vi cơ sở và trả khoang khi huỷ đơn.
 *
 * <p>Chuyển trạng thái đi qua service, khoá theo {@code unit_id}. Các chuyển khác (đặt cọc, bàn giao,
 * bảo trì) thuộc luồng sau, chưa có method ở đây.
 */
@Entity
@Table(name = "storage_units")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "unit_type_id", nullable = false)
    private Long unitTypeId;

    @Convert(converter = StorageUnitStatusConverter.class)
    @Column(nullable = false, length = 20)
    private StorageUnitStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Cancellation Cascade: khoang đang giữ cho đơn bị huỷ được giải phóng {@code Reserved -> Available}. */
    public boolean releaseIfReserved() {
        if (status != StorageUnitStatus.RESERVED) {
            return false;
        }
        this.status = StorageUnitStatus.AVAILABLE;
        this.updatedAt = Instant.now();
        return true;
    }
}
