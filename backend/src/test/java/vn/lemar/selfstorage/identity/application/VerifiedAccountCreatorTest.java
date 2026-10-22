package vn.lemar.selfstorage.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.identity.repository.RoleRepository;

@ExtendWith(MockitoExtension.class)
class VerifiedAccountCreatorTest {

    private static final String EMAIL = "customer@example.com";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuditRecorder auditRecorder;

    @Test
    void createsVerifiedCustomerAccountAndAuditInOneUnitOfWork() {
        VerifiedAccountCreator creator = new VerifiedAccountCreator(accountRepository, roleRepository, auditRecorder);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER")));
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 42L);
            return account;
        });

        Account account = creator.create(EMAIL, "password-hash");

        assertThat(account.getEmail()).isEqualTo(EMAIL);
        assertThat(account.getPasswordHash()).isEqualTo("password-hash");
        assertThat(account.getRole().getName()).isEqualTo("CUSTOMER");
        assertThat(account.isVerified()).isTrue();
        verify(auditRecorder).record(new AuditEntry(AuditAction.ACCOUNT_REGISTERED, AuditEntityType.ACCOUNT,
                "42", null, null, null, 42L));
    }

    @Test
    void missingCustomerRoleFailsBeforeSaving() {
        VerifiedAccountCreator creator = new VerifiedAccountCreator(accountRepository, roleRepository, auditRecorder);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creator.create(EMAIL, "password-hash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CUSTOMER");
        verify(accountRepository, never()).saveAndFlush(any());
        verify(auditRecorder, never()).record(any());
    }

    @Test
    void duplicateEmailPropagatesWithoutAudit() {
        VerifiedAccountCreator creator = new VerifiedAccountCreator(accountRepository, roleRepository, auditRecorder);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER")));
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> creator.create(EMAIL, "password-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(auditRecorder, never()).record(any());
    }
}
