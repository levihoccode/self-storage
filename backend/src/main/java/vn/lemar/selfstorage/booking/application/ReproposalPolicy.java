package vn.lemar.selfstorage.booking.application;

import java.time.Instant;

/**
 * Điều kiện duy nhất để FM được đề xuất lại khoang cho một đơn — dùng chung cho hàng đợi yêu cầu,
 * màn theo dõi đơn thuê và chi tiết đơn.
 *
 * <p>Phải khớp với guard của {@code ProposalService#repropose}
 * ({@code POST /api/rental-orders/{orderId}/proposals}): đơn đóng, đơn quá {@code expires_at}, còn
 * proposal {@code Pending}, hoặc đơn không ở trạng thái cho phép đều trả 409. Lệch một điều kiện là
 * FE mời FM bấm vào một nút chắc chắn lỗi.
 *
 * <p>Đừng nhầm với cờ {@code rental_orders.needs_reproposal} — đó là tín hiệu nghiệp vụ do luồng
 * từ chối/thanh toán bật, chỉ là <em>một</em> đầu vào ở đây.
 */
public final class ReproposalPolicy {

    private ReproposalPolicy() {
    }

    /**
     * @param orderStatus          trạng thái {@code RentalOrder}; null khi request chưa convert
     * @param orderExpiresAt       hạn hoàn thành đơn; null = không hạn
     * @param needsReproposal      cờ raw trên order
     * @param lastProposalStatus   trạng thái proposal mới nhất; null nếu chưa có
     * @param pendingProposalCount số proposal {@code Pending} của đơn
     * @param now                  mốc so hạn
     */
    public static boolean canRepropose(String orderStatus, Instant orderExpiresAt, boolean needsReproposal,
            String lastProposalStatus, long pendingProposalCount, Instant now) {
        if (orderStatus == null
                || "Canceled".equals(orderStatus) || "Done".equals(orderStatus)
                || (orderExpiresAt != null && orderExpiresAt.isBefore(now))
                || pendingProposalCount > 0
                || "Agreed".equals(lastProposalStatus)) {
            return false;
        }
        return ("Pending".equals(orderStatus) || needsReproposal)
                && (lastProposalStatus == null
                        || "Rejected".equals(lastProposalStatus)
                        || "Canceled".equals(lastProposalStatus));
    }
}
