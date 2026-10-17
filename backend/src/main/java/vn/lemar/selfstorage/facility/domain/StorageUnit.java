package vn.lemar.selfstorage.facility.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Khoang chứa — chỉ map các cột cần để đọc trạng thái và phạm vi cơ sở.
 *
 * <p>Chưa có domain method đổi trạng thái: mọi chuyển trạng thái khoang thuộc luồng khác (đặt cọc,
 * bàn giao, bảo trì) và phải đi qua service của luồng đó, khoá theo {@code unit_id}.
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
}
