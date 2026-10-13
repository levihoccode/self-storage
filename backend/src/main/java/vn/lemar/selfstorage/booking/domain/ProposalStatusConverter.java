package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Quy đổi giữa enum viết HOA trong Java và giá trị PascalCase mà CHECK constraint yêu cầu. */
@Converter
public class ProposalStatusConverter implements AttributeConverter<ProposalStatus, String> {

    @Override
    public String convertToDatabaseColumn(ProposalStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public ProposalStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProposalStatus.fromDbValue(dbData);
    }
}
