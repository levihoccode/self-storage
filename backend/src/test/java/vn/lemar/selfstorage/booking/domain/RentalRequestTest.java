package vn.lemar.selfstorage.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class RentalRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    @Test
    void approvedRequestBeforeExpiryIsConvertible() {
        assertThat(request(RentalRequestStatus.APPROVED, NOW.plusSeconds(1), 1L).isConvertibleAt(NOW)).isTrue();
    }

    @Test
    void requestAtOrAfterExpiryIsNotConvertible() {
        assertThat(request(RentalRequestStatus.APPROVED, NOW, 1L).isConvertibleAt(NOW)).isFalse();
        assertThat(request(RentalRequestStatus.APPROVED, NOW.minusSeconds(1), 1L).isConvertibleAt(NOW)).isFalse();
        assertThat(request(RentalRequestStatus.APPROVED, null, 1L).isConvertibleAt(NOW)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = RentalRequestStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void onlyApprovedRequestIsConvertible(RentalRequestStatus status) {
        RentalRequest request = request(status, NOW.plusSeconds(60), 1L);

        assertThat(request.isConvertibleAt(NOW)).isFalse();
        assertThatThrownBy(request::markConverted).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markConvertedMovesApprovedToConverted() {
        RentalRequest request = request(RentalRequestStatus.APPROVED, NOW.plusSeconds(60), 1L);

        request.markConverted();

        assertThat(request.getStatus()).isEqualTo(RentalRequestStatus.CONVERTED);
    }

    @Test
    void approvedRequestWithoutUnitCannotBeConverted() {
        RentalRequest request = request(RentalRequestStatus.APPROVED, NOW.plusSeconds(60), null);

        assertThatThrownBy(request::markConverted).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(RentalRequestStatus.class)
    void statusRoundTripsThroughConverter(RentalRequestStatus status) {
        RentalRequestStatusConverter converter = new RentalRequestStatusConverter();

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(status))).isEqualTo(status);
    }

    @Test
    void converterPassesNullAndRejectsUnknownValue() {
        RentalRequestStatusConverter converter = new RentalRequestStatusConverter();

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> RentalRequestStatus.fromDbValue("APPROVED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openOrderStartsPendingWithoutUnit() {
        RentalOrder order = RentalOrder.open(3L, 5L, NOW);

        assertThat(order.getRequestId()).isEqualTo(3L);
        assertThat(order.getCustomerId()).isEqualTo(5L);
        assertThat(order.getStatus()).isEqualTo(RentalOrderStatus.PENDING);
        assertThat(order.getUnitId()).isNull();
        assertThat(order.getExpiresAt()).isEqualTo(NOW);
    }

    private static RentalRequest request(RentalRequestStatus status, Instant expiresAt, Long unitId) {
        RentalRequest request = new RentalRequest();
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(request, "expiresAt", expiresAt);
        ReflectionTestUtils.setField(request, "unitId", unitId);
        return request;
    }
}
