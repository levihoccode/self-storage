package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Quy đổi giữa enum viết HOA trong Java và giá trị PascalCase mà CHECK constraint yêu cầu. */
@Converter
public class RentalRequestStatusConverter implements AttributeConverter<RentalRequestStatus, String> {

    @Override
    public String convertToDatabaseColumn(RentalRequestStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public RentalRequestStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RentalRequestStatus.fromDbValue(dbData);
    }
}
