package vn.lemar.selfstorage.booking.application.dto;

import java.util.List;

public record FmTransactionPageView(
        List<FmTransactionView> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
