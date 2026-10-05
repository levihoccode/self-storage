package vn.lemar.selfstorage.identity.application.exception;

import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.identity.domain.AccountStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNotAllowedExceptionTest {

    @Test
    void nonBannedStatusGetsGenericMessage() {
        AccountNotAllowedException exception = new AccountNotAllowedException(AccountStatus.ACTIVE);

        assertThat(exception.getMessage()).isEqualTo("Tài khoản không thể đăng nhập");
        assertThat(exception.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
