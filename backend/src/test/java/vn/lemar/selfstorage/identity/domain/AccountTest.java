package vn.lemar.selfstorage.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest {

    @Test
    void newAccountNormalizesEmailAndDefaultsToActive() {
        Account account = new Account(" Customer@Example.com ", "hash", new Role("CUSTOMER"));

        assertThat(account.getEmail()).isEqualTo("customer@example.com");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.isVerified()).isFalse();
        assertThat(account.isLoginAllowed()).isTrue();
    }

    @Test
    void normalizeHandlesNull() {
        assertThat(Account.normalize(null)).isNull();
    }

    @Test
    void markEmailVerifiedSetsTimestampWithoutChangingStatus() {
        Account account = new Account("customer@example.com", "hash", new Role("CUSTOMER"));

        account.markEmailVerified();

        assertThat(account.isVerified()).isTrue();
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void bannedAccountCannotLoginEvenWhenVerified() {
        Account account = new Account("customer@example.com", "hash", new Role("CUSTOMER"));
        account.markEmailVerified();
        account.setStatus(AccountStatus.BANNED);

        assertThat(account.isLoginAllowed()).isFalse();
    }
}
