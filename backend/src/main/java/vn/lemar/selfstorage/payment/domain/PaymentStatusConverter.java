package vn.lemar.selfstorage.payment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Quy đổi giữa enum viết HOA trong Java và giá trị viết thường mà CHECK constraint yêu cầu. */
@Converter
public class PaymentStatusConverter implements AttributeConverter<PaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(PaymentStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public PaymentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PaymentStatus.fromDbValue(dbData);
    }
}
