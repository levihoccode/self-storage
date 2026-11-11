package vn.lemar.selfstorage.handover.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.lemar.selfstorage.SecurityConfig;
import vn.lemar.selfstorage.handover.application.ContractQueryService;
import vn.lemar.selfstorage.handover.application.dto.ContractSummaryResponse;
import vn.lemar.selfstorage.identity.application.AccountJwtAuthenticationConverter;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.identity.config.JwtConfiguration;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.AccountStatus;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

/** HTTP contract của `GET /api/contracts`: bắt buộc token, và service nhận đúng account của người gọi. */
@WebMvcTest(controllers = ContractController.class)
@Import({SecurityConfig.class, JwtConfiguration.class, AccountJwtAuthenticationConverter.class})
@TestPropertySource(properties = "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class ContractControllerTest {

    private static final String CUSTOMER = "customer1@lemar.vn";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private ContractQueryService contractQueryService;
    @MockBean
    private AccountQueryService accountQueryService;
    @MockBean
    private AccountRepository accountRepository;

    @Test
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/contracts")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsContractsOfTheCallingAccount() throws Exception {
        Account account = org.mockito.Mockito.mock(Account.class);
        when(account.getEmail()).thenReturn(CUSTOMER);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.getRole()).thenReturn(new Role("CUSTOMER"));
        when(account.isLoginAllowed()).thenReturn(true);
        when(accountRepository.findWithRoleByEmail(CUSTOMER)).thenReturn(Optional.of(account));
        when(accountQueryService.requireIdByEmail(CUSTOMER)).thenReturn(7L);
        when(contractQueryService.listForCustomer(7L)).thenReturn(List.of(new ContractSummaryResponse(
                1L, "HD-Q7-0001", "Active", 3L, 1L, "A-01", "1.2 × 2 × 2.2 m", "Small",
                1L, "Self Storage Quận 7", "123 Nguyễn Văn Linh, Quận 7, TP.HCM",
                new BigDecimal("500000.00"), new BigDecimal("500000.00"), 6,
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 5, 1), Instant.parse("2026-11-01T02:00:00Z"),
                true, false)));

        mockMvc.perform(get("/api/contracts").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(ContractController.LIST_MESSAGE))
                .andExpect(jsonPath("$.data[0].unitCode").value("A-01"))
                .andExpect(jsonPath("$.data[0].facilityName").value("Self Storage Quận 7"))
                .andExpect(jsonPath("$.data[0].overdue").value(true));

        verify(contractQueryService).listForCustomer(7L);
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
