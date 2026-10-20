package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Quy đổi enum viết HOA trong Java sang giá trị CHECK constraint yêu cầu. */
@Converter
public class RentalOrderStatusConverter implements AttributeConverter<RentalOrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(RentalOrderStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public RentalOrderStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RentalOrderStatus.fromDbValue(dbData);
    }
}
