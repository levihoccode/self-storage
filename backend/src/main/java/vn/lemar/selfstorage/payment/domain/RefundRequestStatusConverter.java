package vn.lemar.selfstorage.payment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RefundRequestStatusConverter
        implements AttributeConverter<RefundRequestStatus, String> {

    @Override
    public String convertToDatabaseColumn(RefundRequestStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public RefundRequestStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RefundRequestStatus.fromDbValue(dbData);
    }
}
