package vn.lemar.selfstorage.identity.application.exception;

public class VerificationResendTooEarlyException extends RuntimeException {
    public VerificationResendTooEarlyException() {
        super("Vui lòng chờ trước khi yêu cầu gửi lại mã.");
    }
}
