package vn.lemar.selfstorage.booking.application;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.booking.application.dto.FmTransactionPageView;
import vn.lemar.selfstorage.booking.application.dto.FmTransactionView;
import vn.lemar.selfstorage.booking.application.exception.FmTransactionFilterException;
import vn.lemar.selfstorage.booking.repository.FmTransactionReadRepository;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.payment.application.PaymentSession;

/**
 * Lịch sử dòng tiền của cơ sở cho FM — read-only, gộp invoice/fee, payment và refund.
 * Quyền {@code invoice.read} + facility scope; filter/trang áp trên union để total khớp dữ liệu.
 */
@Service
public class FmTransactionQueryService {

    private static final Set<String> KINDS = Set.of("INVOICE", "FEE", "PAYMENT", "REFUND");
    private static final Set<String> DIRECTIONS = Set.of("IN", "OUT");
    private static final Set<String> STATUSES = Set.of(
            "Unpaid", "Paid", "Canceled", "Expired",
            "Pending", "Failed", "Success", "Duplicate");
    private static final Set<String> REFUND_STATUSES = Set.of(
            "None", "Required", "Processing", "AwaitingConfirmation", "Refunded");
    private static final int MAX_PAGE_SIZE = 100;

    private final FmTransactionReadRepository readRepository;
    private final Access access;

    public FmTransactionQueryService(FmTransactionReadRepository readRepository, Access access) {
        this.readRepository = readRepository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public FmTransactionPageView listForFacility(Long facilityId, String kind, String direction,
            String status, String refundStatus, boolean needsReconciliation, String from, String to,
            int page, int size) {
        access.can("invoice.read", facilityId);
        String normalizedKind = normalize(KINDS, kind, "Loại giao dịch");
        String normalizedDirection = normalize(DIRECTIONS, direction, "Chiều dòng tiền");
        String normalizedStatus = normalize(STATUSES, status, "Trạng thái giao dịch");
        String normalizedRefundStatus = normalize(REFUND_STATUSES, refundStatus, "Trạng thái hoàn tiền");
        Instant fromInstant = parseInstant(from, "Thời điểm bắt đầu");
        Instant toInstant = parseInstant(to, "Thời điểm kết thúc");
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new FmTransactionFilterException("Khoảng thời gian không hợp lệ: from sau to");
        }
        if (page < 0) {
            throw new FmTransactionFilterException("Số trang không hợp lệ: " + page);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new FmTransactionFilterException("Kích thước trang phải từ 1 đến " + MAX_PAGE_SIZE);
        }

        Instant pendingCutoff = needsReconciliation
                ? Instant.now().minus(PaymentSession.WINDOW)
                : null;
        long total = readRepository.count(facilityId, normalizedKind, normalizedDirection,
                normalizedStatus, normalizedRefundStatus, pendingCutoff, fromInstant, toInstant);
        List<FmTransactionReadRepository.TransactionRow> rows = readRepository.findPage(
                facilityId, normalizedKind, normalizedDirection, normalizedStatus,
                normalizedRefundStatus, pendingCutoff, fromInstant, toInstant,
                (long) page * size, size);
        Instant now = Instant.now();
        List<FmTransactionView> items = rows.stream()
                .map(row -> toView(row, now))
                .toList();
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new FmTransactionPageView(items, page, size, total, totalPages);
    }

    private static FmTransactionView toView(FmTransactionReadRepository.TransactionRow row,
            Instant now) {
        boolean pending = "PAYMENT".equals(row.kind()) && "Pending".equals(row.status());
        long pendingAgeMinutes = pending && row.occurredAt() != null
                ? Math.max(0, Duration.between(row.occurredAt(), now).toMinutes())
                : 0;
        boolean needsReconciliation = pending
                && pendingAgeMinutes >= PaymentSession.WINDOW.toMinutes();
        return new FmTransactionView(row.id(), row.kind(), row.direction(), row.amount(), row.status(),
                row.occurredAt(), row.orderId(), row.invoiceId(), row.invoiceCode(),
                row.paymentTransactionId(), row.refundTransactionId(), row.code(), row.method(),
                row.description(), row.customerId(), row.customerName(), row.customerEmail(),
                row.facilityId(), row.facilityName(), row.unitCode(), row.invoiceRefundStatus(),
                row.invoiceRefundReason(), pendingAgeMinutes, needsReconciliation,
                row.refundConfirmedAt(), row.refundDisputedAt(), row.refundDisputeNote());
    }

    private static String normalize(Set<String> allowed, String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (!allowed.contains(normalized)) {
            throw new FmTransactionFilterException(label + " không hợp lệ: " + value);
        }
        return normalized;
    }

    /** {@code from}/{@code to} là ISO-8601 UTC, ví dụ {@code 2026-11-01T00:00:00Z}; {@code to} loại trừ. */
    private static Instant parseInstant(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException ex) {
            throw new FmTransactionFilterException(label + " không hợp lệ (cần ISO-8601 UTC): " + value);
        }
    }
}
