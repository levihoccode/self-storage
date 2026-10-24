package vn.lemar.selfstorage.booking.application;

import java.math.BigDecimal;
import java.time.Instant;
import vn.lemar.selfstorage.booking.domain.Invoice;

/**
 * Ảnh chụp hoá đơn sau thao tác — controller (PR sau) dựng response / điều hướng sang trang hoá đơn.
 *
 * @param type   giá trị PascalCase như trong DB ({@code Deposit}, ...)
 * @param status giá trị PascalCase như trong DB ({@code Unpaid}, ...)
 */
public record InvoiceView(Long id, Long orderId, String code, String title, String description, String type,
                          String status, BigDecimal amount, Instant dueDate) {

    static InvoiceView of(Invoice invoice) {
        return new InvoiceView(invoice.getId(), invoice.getOrderId(), invoice.getCode(), invoice.getTitle(),
                invoice.getDescription(), invoice.getType().dbValue(), invoice.getStatus().dbValue(),
                invoice.getAmount(), invoice.getDueDate());
    }
}
