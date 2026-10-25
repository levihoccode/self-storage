package vn.lemar.selfstorage.booking.application;

import vn.lemar.selfstorage.booking.domain.RentalOrder;

/**
 * Ảnh chụp đơn thuê sau thao tác.
 *
 * @param status giá trị PascalCase như trong DB ({@code Pending}, {@code Canceled}, ...)
 */
public record RentalOrderView(Long id, Long customerId, Long unitId, String status, String cancelReason) {

    static RentalOrderView of(RentalOrder order) {
        return new RentalOrderView(order.getId(), order.getCustomerId(), order.getUnitId(),
                order.getStatus().dbValue(), order.getCancelReason());
    }
}
