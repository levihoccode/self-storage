package vn.lemar.selfstorage.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.lemar.selfstorage.identity.application.exception.UnauthenticatedException;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

class CurrentAccountProviderTest {

    private final AccountRepository repo = mock(AccountRepository.class);
    private final CurrentAccountProvider provider = new CurrentAccountProvider(repo);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthenticationThrows() {
        assertThatThrownBy(provider::getCurrentAccount).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void anonymousThrows() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThatThrownBy(provider::getCurrentAccount).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void unauthenticatedTokenThrows() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("a@x.vn", "pw"));
        assertThatThrownBy(provider::getCurrentAccount).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void unknownEmailThrows() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost@x.vn", "pw", List.of()));
        when(repo.findWithRoleByEmail("ghost@x.vn")).thenReturn(Optional.empty());
        assertThatThrownBy(provider::getCurrentAccount).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void knownEmailReturnsAccount() {
        Account account = mock(Account.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("A@X.vn", "pw", List.of()));
        when(repo.findWithRoleByEmail("a@x.vn")).thenReturn(Optional.of(account));
        assertThat(provider.getCurrentAccount()).isSameAs(account);
    }
}