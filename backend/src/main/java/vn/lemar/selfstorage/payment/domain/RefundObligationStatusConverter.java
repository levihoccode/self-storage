package vn.lemar.selfstorage.payment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RefundObligationStatusConverter
        implements AttributeConverter<RefundObligationStatus, String> {

    @Override
    public String convertToDatabaseColumn(RefundObligationStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public RefundObligationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RefundObligationStatus.fromDbValue(dbData);
    }
}
