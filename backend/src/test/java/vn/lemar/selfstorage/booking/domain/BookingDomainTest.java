package vn.lemar.selfstorage.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Hành vi của ba entity booking và phép quy đổi enum ↔ CHECK constraint.
 *
 * <p>Test nằm cùng package để dùng được constructor {@code protected} của JPA; giá trị field
 * đặt bằng reflection vì entity cố ý không mở setter cho nghiệp vụ.
 */
class BookingDomainTest {

    private static <T> T with(T entity, String field, Object value) {
        try {
            Field f = entity.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(entity, value);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void invoiceBelongsOnlyToItsOwnCustomer() {
        Invoice invoice = with(new Invoice(), "customerId", 7L);

        assertThat(invoice.belongsTo(7L)).isTrue();
        assertThat(invoice.belongsTo(8L)).isFalse();
    }

    @Test
    void invoiceWithoutCustomerBelongsToNobody() {
        assertThat(new Invoice().belongsTo(7L)).isFalse();
    }

    @Test
    void invoiceStartsUnpaidAndMovesOneWay() {
        Invoice invoice = new Invoice();
        assertThat(invoice.isUnpaid()).isTrue();

        invoice.markPaid();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.isUnpaid()).isFalse();
    }

    @Test
    void invoiceCancelMarksItVoid() {
        Invoice invoice = new Invoice();
        invoice.cancel();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELED);
        assertThat(invoice.isUnpaid()).isFalse();
    }

    @Test
    void orderWithoutDeadlineNeverExpires() {
        assertThat(new RentalOrder().isExpiredAt(Instant.now())).isFalse();
    }

    @Test
    void orderExpiresOnlyAfterItsDeadline() {
        Instant deadline = Instant.parse("2026-10-13T00:00:00Z");
        RentalOrder order = with(new RentalOrder(), "expiresAt", deadline);

        assertThat(order.isExpiredAt(deadline.minusSeconds(1))).isFalse();
        assertThat(order.isExpiredAt(deadline.plusSeconds(1))).isTrue();
    }

    @Test
    void depositSwapsTheDeadlineToTheHoldWindow() {
        RentalOrder order = with(new RentalOrder(), "expiresAt", Instant.parse("2026-10-13T00:00:00Z"));
        Instant holdUntil = Instant.parse("2026-11-12T00:00:00Z");

        order.markDeposited(holdUntil);

        assertThat(order.getStatus()).isEqualTo(RentalOrderStatus.DEPOSITED);
        assertThat(order.getExpiresAt()).isEqualTo(holdUntil);
    }

    @Test
    void unitStartsAvailableAndIsHeldByReserving() {
        StorageUnit unit = new StorageUnit();
        assertThat(unit.isAvailable()).isTrue();

        unit.reserve();
        assertThat(unit.getStatus()).isEqualTo(StorageUnitStatus.RESERVED);
        assertThat(unit.isAvailable()).isFalse();
    }

    @Test
    void statusEnumsRoundTripThroughTheirDbSpelling() {
        assertThat(InvoiceStatus.fromDbValue("Paid")).isEqualTo(InvoiceStatus.PAID);
        assertThat(RentalOrderStatus.fromDbValue("InProgress"))
                .isEqualTo(RentalOrderStatus.INPROGRESS);
        assertThat(StorageUnitStatus.fromDbValue("Maintenance"))
                .isEqualTo(StorageUnitStatus.MAINTENANCE);
    }

    @Test
    void unknownDbValueIsARejectedProgrammingError() {
        assertThatThrownBy(() -> InvoiceStatus.fromDbValue("PAID"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RentalOrderStatus.fromDbValue("nope"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageUnitStatus.fromDbValue(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertersMapBothDirectionsAndPassNullThrough() {
        InvoiceStatusConverter invoiceConverter = new InvoiceStatusConverter();
        assertThat(invoiceConverter.convertToDatabaseColumn(InvoiceStatus.CANCELED))
                .isEqualTo("Canceled");
        assertThat(invoiceConverter.convertToEntityAttribute("Unpaid"))
                .isEqualTo(InvoiceStatus.UNPAID);
        assertThat(invoiceConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(invoiceConverter.convertToEntityAttribute(null)).isNull();

        RentalOrderStatusConverter orderConverter = new RentalOrderStatusConverter();
        assertThat(orderConverter.convertToDatabaseColumn(RentalOrderStatus.DEPOSITED))
                .isEqualTo("Deposited");
        assertThat(orderConverter.convertToEntityAttribute("Done")).isEqualTo(RentalOrderStatus.DONE);
        assertThat(orderConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(orderConverter.convertToEntityAttribute(null)).isNull();

        StorageUnitStatusConverter unitConverter = new StorageUnitStatusConverter();
        assertThat(unitConverter.convertToDatabaseColumn(StorageUnitStatus.RENTED))
                .isEqualTo("Rented");
        assertThat(unitConverter.convertToEntityAttribute("Available"))
                .isEqualTo(StorageUnitStatus.AVAILABLE);
        assertThat(unitConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(unitConverter.convertToEntityAttribute(null)).isNull();
    }
}
