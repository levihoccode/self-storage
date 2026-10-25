package vn.lemar.selfstorage.identity.application.exception;

/**
 * Account `ACTIVE` nhưng email chưa xác minh — bị chặn đăng nhập. Xác minh là bắt buộc ngay tại
 * đăng ký (`specs/draft.md` › Business workflow).
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Tài khoản chưa xác minh email");
    }
}
