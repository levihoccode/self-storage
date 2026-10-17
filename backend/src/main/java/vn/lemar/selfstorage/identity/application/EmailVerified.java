package vn.lemar.selfstorage.identity.application;

/**
 * Account vừa xác minh email thành công (không phát lại khi đã xác minh từ trước).
 *
 * <p>Module khác nghe event này thay vì để identity gọi sang — identity là module nền, không được
 * phụ thuộc ngược module nghiệp vụ. Booking dùng nó để claim {@code RentalRequest} (Flow 1.2 Case A).
 */
public record EmailVerified(Long accountId) {
}
