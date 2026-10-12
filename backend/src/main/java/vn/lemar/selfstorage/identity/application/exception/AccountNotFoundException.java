package vn.lemar.selfstorage.identity.application.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("Không tìm thấy tài khoản");
    }
}
