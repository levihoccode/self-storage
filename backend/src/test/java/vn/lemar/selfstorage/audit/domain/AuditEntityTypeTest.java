package vn.lemar.selfstorage.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuditEntityTypeTest {

    @Test
    void fromDbValueRoundTripsEveryType() {
        for (AuditEntityType type : AuditEntityType.values()) {
            assertThat(AuditEntityType.fromDbValue(type.dbValue())).isEqualTo(type);
        }
    }

    @Test
    void fromDbValueRejectsUnknownValue() {
        assertThatThrownBy(() -> AuditEntityType.fromDbValue("Kho"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
