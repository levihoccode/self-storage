package vn.lemar.selfstorage.audit.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Quy đổi giữa tên enum HOA trong Java và giá trị PascalCase mà CHECK constraint yêu cầu. */
@Converter
public class AuditEntityTypeConverter implements AttributeConverter<AuditEntityType, String> {

    @Override
    public String convertToDatabaseColumn(AuditEntityType attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public AuditEntityType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AuditEntityType.fromDbValue(dbData);
    }
}
