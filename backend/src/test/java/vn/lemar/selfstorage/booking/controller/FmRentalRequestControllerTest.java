package vn.lemar.selfstorage.booking.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.lemar.selfstorage.SecurityConfig;
import vn.lemar.selfstorage.booking.application.RentalRequestService;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestPageResponse;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.AccountJwtAuthenticationConverter;
import vn.lemar.selfstorage.identity.application.AuthService;
import vn.lemar.selfstorage.identity.application.exception.ForbiddenException;
import vn.lemar.selfstorage.identity.config.JwtConfiguration;
import vn.lemar.selfstorage.identity.controller.AuthController;
import vn.lemar.selfstorage.identity.controller.AuthExceptionHandler;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.AccountStatus;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

import static org.mockito.Mockito.mock;

@WebMvcTest(controllers = {FmRentalRequestController.class, AuthController.class})
@Import({SecurityConfig.class, JwtConfiguration.class, AccountJwtAuthenticationConverter.class,
        BookingExceptionHandler.class, AuthExceptionHandler.class})
@TestPropertySource(properties = "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class FmRentalRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private RentalRequestService rentalRequestService;

    @MockBean
    private Access access;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private AuthService authService;

    @Test
    void fmQueueReturnsPage() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.queue(eq(1L), eq(null), eq(0), eq(20)))
                .thenReturn(new RentalRequestPageResponse(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/fm/rental-requests?facilityId=1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Lấy danh sách yêu cầu đặt kho thành công"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void crossFacilityReturns403() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        doThrow(new ForbiddenException("Bạn không được gán vào cơ sở này"))
                .when(access).can(eq("rental_request.read"), eq(2L));

        mockMvc.perform(get("/api/fm/rental-requests?facilityId=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không được gán vào cơ sở này"));
    }

    @Test
    void anonymousQueueReturns401() throws Exception {
        mockMvc.perform(get("/api/fm/rental-requests?facilityId=1"))
                .andExpect(status().isUnauthorized());
    }

    private void authenticate(String email, String roleName) {
        Role role = mock(Role.class);
        org.mockito.Mockito.when(role.getName()).thenReturn(roleName);
        Account account = mock(Account.class);
        org.mockito.Mockito.when(account.getEmail()).thenReturn(email);
        org.mockito.Mockito.when(account.getRole()).thenReturn(role);
        org.mockito.Mockito.when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        org.mockito.Mockito.when(account.isLoginAllowed()).thenReturn(true);
        when(accountRepository.findWithRoleByEmail(email)).thenReturn(Optional.of(account));
    }

    private String tokenFor(String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("self-storage").issuedAt(now)
                .expiresAt(now.plusSeconds(60)).claim("email", email).build();
        return jwtEncoder.encode(JwtEncoderParameters
                .from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
