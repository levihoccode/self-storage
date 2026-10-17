package vn.lemar.selfstorage.booking.application.exception;

/**
 * Request vẫn {@code Pending} nhưng đã quá {@code expires_at} (cron chưa chuyển kịp sang
 * {@code Expired}) — FM không được duyệt, trả 409 (spec draft.md §1.1).
 */
public class RentalRequestExpiredException extends RuntimeException {

    public RentalRequestExpiredException() {
        super("Yêu cầu đã quá hạn xử lý");
    }
}
