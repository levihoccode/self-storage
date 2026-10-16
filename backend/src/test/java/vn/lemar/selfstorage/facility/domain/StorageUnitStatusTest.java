package vn.lemar.selfstorage.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.lemar.selfstorage.facility.application.StorageUnitView;

class StorageUnitStatusTest {

    private final StorageUnitStatusConverter converter = new StorageUnitStatusConverter();

    @ParameterizedTest
    @EnumSource(StorageUnitStatus.class)
    void roundTripsThroughConverter(StorageUnitStatus status) {
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(status))).isEqualTo(status);
    }

    @Test
    void passesNullThroughAndRejectsUnknownValues() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> StorageUnitStatus.fromDbValue("AVAILABLE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void releaseIsANoOpUnlessUnitIsReserved() {
        assertThat(new StorageUnit().releaseIfReserved()).isFalse();
    }

    @Test
    void onlyAvailableUnitIsAvailable() {
        assertThat(new StorageUnitView(1L, "A-01", 1L, 1L, "Available").isAvailable()).isTrue();
        assertThat(new StorageUnitView(1L, "A-01", 1L, 1L, "Reserved").isAvailable()).isFalse();
    }
}
