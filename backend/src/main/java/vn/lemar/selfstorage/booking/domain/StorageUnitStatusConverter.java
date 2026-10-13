package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Quy đổi enum viết HOA trong Java sang giá trị CHECK constraint yêu cầu. */
@Converter
public class StorageUnitStatusConverter implements AttributeConverter<StorageUnitStatus, String> {

    @Override
    public String convertToDatabaseColumn(StorageUnitStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public StorageUnitStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StorageUnitStatus.fromDbValue(dbData);
    }
}
