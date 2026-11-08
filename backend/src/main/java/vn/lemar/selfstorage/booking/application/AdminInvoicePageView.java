package vn.lemar.selfstorage.booking.application;

import java.util.List;

/** Một trang hoá đơn cho ADMIN — read-only, không kèm dữ liệu thanh toán nhạy cảm. */
public record AdminInvoicePageView(List<InvoiceListView> items, int page, int size,
                                   long totalElements, int totalPages) {
}
