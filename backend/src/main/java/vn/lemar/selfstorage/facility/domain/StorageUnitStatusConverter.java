package vn.lemar.selfstorage.facility.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Quy đổi giữa enum viết HOA trong Java và giá trị PascalCase mà CHECK constraint yêu cầu. */
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
