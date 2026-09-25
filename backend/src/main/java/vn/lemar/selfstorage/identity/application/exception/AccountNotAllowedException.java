package vn.lemar.selfstorage.identity.application.exception;

import vn.lemar.selfstorage.identity.domain.AccountStatus;

public class AccountNotAllowedException extends RuntimeException {

    private final AccountStatus status;

    public AccountNotAllowedException(AccountStatus status) {
        super(buildMessage(status));
        this.status = status;
    }

    private static String buildMessage(AccountStatus status) {
        return switch (status) {
            case UNVERIFIED -> "Tài khoản chưa xác thực email";
            case LOCKED -> "Tài khoản đang bị khóa";
            case BANNED -> "Tài khoản đã bị cấm";
            default -> "Tài khoản không thể đăng nhập";
        };
    }

    public AccountStatus getStatus() {
        return status;
    }
}