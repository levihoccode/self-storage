package vn.lemar.selfstorage.handover.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.lemar.selfstorage.SecurityConfig;
import vn.lemar.selfstorage.handover.application.UnitOverlockService;
import vn.lemar.selfstorage.handover.application.UnitOverlockService.OverlockView;
import vn.lemar.selfstorage.handover.application.exception.HandoverConflictException;
import vn.lemar.selfstorage.identity.application.AccountJwtAuthenticationConverter;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.identity.config.JwtConfiguration;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.AccountStatus;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

/** HTTP contract khoá chặn của FS + map lỗi handover (409) + chặn sai role (403). */
@WebMvcTest(controllers = {StaffOverlockController.class, FmOverlockController.class})
@Import({SecurityConfig.class, JwtConfiguration.class, AccountJwtAuthenticationConverter.class,
    HandoverExceptionHandler.class})
@TestPropertySource(properties = "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class StaffOverlockControllerTest {

    private static final String FS = "fs@example.com";
    private static final String CUSTOMER = "customer@example.com";
    private static final String FM = "fm@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private UnitOverlockService overlocks;
    @MockBean
    private CurrentAccountProvider currentAccountProvider;
    @MockBean
    private AccountRepository accountRepository;

    @Test
    void staffListsAppliesAndRemovesOverlocks() throws Exception {
        login(FS, "FS", 5L);
        when(overlocks.staffQueue()).thenReturn(List.of(view("Required")));
        when(overlocks.apply(eq(3L), eq("Khoá số 12"))).thenReturn(view("Applied"));
        when(overlocks.remove(eq(3L), isNull())).thenThrow(
                new HandoverConflictException("Hợp đồng vẫn đang tạm khoá truy cập — chưa gỡ khoá chặn được"));

        mockMvc.perform(as(get("/api/staff/overlocks"), FS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("Required"))
                .andExpect(jsonPath("$.data[0].unitCode").value("A-01"));
        mockMvc.perform(as(post("/api/staff/overlocks/3/apply"), FS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Khoá số 12\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("Applied"));
        mockMvc.perform(as(post("/api/staff/overlocks/3/remove"), FS))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(
                        "Hợp đồng vẫn đang tạm khoá truy cập — chưa gỡ khoá chặn được"));
    }

    @Test
    void managerListsAndAssignsOverlocks() throws Exception {
        login(FM, "FM", 2L);
        when(overlocks.facilityQueue(1L)).thenReturn(List.of(view("Required")));
        when(overlocks.assign(3L, 5L)).thenReturn(view("Required"));

        mockMvc.perform(as(get("/api/fm/overlocks?facilityId=1"), FM))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(3));
        mockMvc.perform(as(post("/api/fm/overlocks/3/assign"), FM).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("Required"));

        login(FS, "FS", 5L);
        mockMvc.perform(as(get("/api/fm/overlocks?facilityId=1"), FS)).andExpect(status().isForbidden());
    }

    @Test
    void customerCannotTouchOverlocks() throws Exception {
        login(CUSTOMER, "CUSTOMER", 6L);
        when(overlocks.staffQueue()).thenReturn(List.of());
        when(overlocks.apply(any(), any())).thenReturn(view("Applied"));

        mockMvc.perform(as(get("/api/staff/overlocks"), CUSTOMER)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/staff/overlocks/3/apply"), CUSTOMER)).andExpect(status().isForbidden());
    }

    private static OverlockView view(String status) {
        return new OverlockView(3L, 7L, "HD-1", 4L, 9L, "A-01", 1L, "Kho Q7", status, Instant.now(), null, null,
                null, null, null, null, null);
    }

    private void login(String email, String roleName, Long accountId) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        Account account = mock(Account.class);
        when(account.getEmail()).thenReturn(email);
        when(account.getRole()).thenReturn(role);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.isLoginAllowed()).thenReturn(true);
        when(accountRepository.findWithRoleByEmail(email)).thenReturn(Optional.of(account));
        when(currentAccountProvider.getCurrentAccountId()).thenReturn(accountId);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String email) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(email));
    }

    private String tokenFor(String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self-storage")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("email", email)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
