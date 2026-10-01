package vn.lemar.selfstorage.identify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.identity.application.JwtService;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.RoleName;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.identity.repository.RoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AccessFacilityScopeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PING = "/api/fm/ping-facility/";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private long facilityA;
    private long facilityB;

    @BeforeEach
    void pickTwoFacilities() {
        List<Long> ids = jdbc.queryForList(
                "select id from facilities order by id limit 2", Long.class);
        if (ids.size() < 2) {
            throw new IllegalStateException("Seed cần ít nhất 2 facility để test chéo cơ sở");
        }
        facilityA = ids.get(0);
        facilityB = ids.get(1);
    }

    private Account newAccount(RoleName role) {
        Account account = new Account(
                role.name().toLowerCase() + "-scope@test.local",
                passwordEncoder.encode("Abc12345"),
                roleRepository.findByName(role.name()).orElseThrow());
        account.markEmailVerified();
        return accountRepository.saveAndFlush(account);
    }

    private void assign(Account account, long facilityId) {
        jdbc.update("insert into account_facility_assignments (account_id, facility_id) values (?, ?)",
                account.getId(), facilityId);
    }

    private String bearer(Account account) {
        return "Bearer " + jwtService.generateAccessToken(account);
    }

    @Test
    void fmCanAccessAssignedFacility() throws Exception {
        Account fm = newAccount(RoleName.FM);
        assign(fm, facilityA);

        mockMvc.perform(get(PING + facilityA).header("Authorization", bearer(fm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("pong"));
    }

    @Test
    void fmBlockedOnOtherFacility() throws Exception {
        Account fm = newAccount(RoleName.FM);
        assign(fm, facilityA);

        mockMvc.perform(get(PING + facilityB).header("Authorization", bearer(fm)))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongRoleBlockedEvenOnAssignedFacility() throws Exception {
        Account customer = newAccount(RoleName.CUSTOMER);
        assign(customer, facilityA);

        mockMvc.perform(get(PING + facilityA).header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAndBomAccessAnyFacilityWithoutAssignment() throws Exception {
        Account admin = newAccount(RoleName.ADMIN);
        Account bom = newAccount(RoleName.BOM);

        mockMvc.perform(get(PING + facilityB).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get(PING + facilityB).header("Authorization", bearer(bom)))
                .andExpect(status().isOk());
    }
}