package vn.lemar.selfstorage.booking.application;

/**
 * Kết quả khách bấm "Từ chối".
 *
 * @param orderCanceled {@code true} khi lần từ chối này chạm {@code proposal.max_rejection_count} và đơn
 *                      đã chạy Cancellation Cascade — khách phải gửi yêu cầu mới từ đầu
 */
public record RejectResult(ProposalView proposal, boolean orderCanceled) {
}
