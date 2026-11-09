package vn.lemar.selfstorage.payment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Quy đổi refund transaction status giữa enum Java và giá trị PascalCase của DB. */
@Converter
public class RefundTransactionStatusConverter
        implements AttributeConverter<RefundTransactionStatus, String> {

    @Override
    public String convertToDatabaseColumn(RefundTransactionStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public RefundTransactionStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RefundTransactionStatus.fromDbValue(dbData);
    }
}
