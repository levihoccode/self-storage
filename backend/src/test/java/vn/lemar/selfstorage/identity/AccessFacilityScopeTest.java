package vn.lemar.selfstorage.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.identity.application.JwtService;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

/**
 * Phạm vi cơ sở theo spec: ADMIN/BOM toàn cục; FM theo facilities.fm_account_id;
 * FS theo account_facility_assignments. Dùng dữ liệu seed V2/V4:
 * fm1 -> Q7, fm2 -> TD, fs1 -> Q7.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AccessFacilityScopeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PING = "/api/facility-access/ping/";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private long q7;
    private long td;

    @BeforeEach
    void loadFacilities() {
        q7 = facilityId("Q7");
        td = facilityId("TD");
    }

    private long facilityId(String code) {
        return jdbc.queryForObject("select id from facilities where code = ?", Long.class, code);
    }

    private String bearerOf(String email) {
        Account account = accountRepository.findByEmail(email).orElseThrow();
        return "Bearer " + jwtService.issueAccessToken(account);
    }

    @Test
    void fmAccessesOwnFacilityViaFmAccountId() throws Exception {
        mockMvc.perform(get(PING + q7).header("Authorization", bearerOf("fm1@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("pong"));
        mockMvc.perform(get(PING + td).header("Authorization", bearerOf("fm2@lemar.vn")))
                .andExpect(status().isOk());
    }

    @Test
    void fmBlockedOnOtherFacility() throws Exception {
        mockMvc.perform(get(PING + td).header("Authorization", bearerOf("fm1@lemar.vn")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PING + q7).header("Authorization", bearerOf("fm2@lemar.vn")))
                .andExpect(status().isForbidden());
    }

    @Test
    void fsAccessesAssignedFacilityOnly() throws Exception {
        mockMvc.perform(get(PING + q7).header("Authorization", bearerOf("fs1@lemar.vn")))
                .andExpect(status().isOk());
        mockMvc.perform(get(PING + td).header("Authorization", bearerOf("fs1@lemar.vn")))
                .andExpect(status().isForbidden());
    }
    @Test
    void customerBlocked() throws Exception {
        mockMvc.perform(get(PING + q7).header("Authorization", bearerOf("customer1@lemar.vn")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAndBomAccessAnyFacilityWithoutAssignment() throws Exception {
        mockMvc.perform(get(PING + td).header("Authorization", bearerOf("admin@lemar.vn")))
                .andExpect(status().isOk());
        mockMvc.perform(get(PING + td).header("Authorization", bearerOf("bom1@lemar.vn")))
                .andExpect(status().isOk());
    }
    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get(PING + q7))
                .andExpect(status().isUnauthorized());
    }
}
