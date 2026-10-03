package vn.lemar.selfstorage.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.identity.application.AuthService;
import vn.lemar.selfstorage.identity.application.JwtService;
import vn.lemar.selfstorage.identity.application.dto.LoginRequest;
import vn.lemar.selfstorage.identity.application.dto.LoginResponse;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.domain.RoleName;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.identity.repository.RoleRepository;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AuthRoleLoginTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PASSWORD = "Abc12345";

    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthService authService;
    @Autowired
    private JwtService jwtService;

    @Test
    void seededRolesMatchEnum() {
        Set<String> inDb = roleRepository.findAll().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        Set<String> inEnum = Arrays.stream(RoleName.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(inDb).isEqualTo(inEnum);
    }

    @ParameterizedTest
    @EnumSource(RoleName.class)
    void everyRoleCanLogin(RoleName roleName) {
        Role role = roleRepository.findByName(roleName.name()).orElseThrow();
        String email = roleName.name().toLowerCase() + "@test.local";

        Account account = new Account(email, passwordEncoder.encode(PASSWORD), role);
        account.markEmailVerified();
        accountRepository.saveAndFlush(account);

        LoginResponse response = authService.login(new LoginRequest(email, PASSWORD));

        assertThat(response.accountId()).isEqualTo(account.getId());
        assertThat(response.email()).isEqualTo(email);
        assertThat(response.role()).isEqualTo(roleName.name());
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresInSeconds()).isPositive();

        var claims = jwtService.parseAndValidate(response.accessToken());
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(account.getId()));
        assertThat(claims.get("email", String.class)).isEqualTo(email);
        assertThat(claims.get("role", String.class)).isEqualTo(roleName.name());
    }
}
