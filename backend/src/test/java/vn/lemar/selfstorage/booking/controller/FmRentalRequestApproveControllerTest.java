package vn.lemar.selfstorage.booking.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.lemar.selfstorage.SecurityConfig;
import vn.lemar.selfstorage.booking.application.RentalRequestService;
import vn.lemar.selfstorage.booking.application.dto.ApproveRentalRequestResponse;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestAlreadyHandledException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitUnavailableException;
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

@WebMvcTest(controllers = {FmRentalRequestApproveController.class, AuthController.class})
@Import({SecurityConfig.class, JwtConfiguration.class, AccountJwtAuthenticationConverter.class,
        BookingExceptionHandler.class, AuthExceptionHandler.class})
@TestPropertySource(properties = "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class FmRentalRequestApproveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private RentalRequestService rentalRequestService;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private AuthService authService;

    @Test
    void approveReturnsApproved() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        Instant now = Instant.now();
        when(rentalRequestService.approve(eq(1L), eq(10L))).thenReturn(
                new ApproveRentalRequestResponse(1L, "Approved", 10L, now,
                        now.plusSeconds(7L * 24L * 60L * 60L)));

        mockMvc.perform(post("/api/fm/rental-requests/1/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã duyệt yêu cầu đặt kho"))
                .andExpect(jsonPath("$.data.status").value("Approved"))
                .andExpect(jsonPath("$.data.unitId").value(10));
    }

    @Test
    void approveHandledTwiceReturns409() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.approve(eq(1L), eq(10L)))
                .thenThrow(new RentalRequestAlreadyHandledException());

        mockMvc.perform(post("/api/fm/rental-requests/1/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Yêu cầu đã được xử lý bởi FM khác"));
    }

    @Test
    void approveUnavailableUnitReturns409() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.approve(eq(1L), eq(11L)))
                .thenThrow(new StorageUnitUnavailableException());

        mockMvc.perform(post("/api/fm/rental-requests/1/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":11}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Khoang đã không còn khả dụng, vui lòng chọn khoang khác"));
    }

    @Test
    void approveUnknownRequestReturns404() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.approve(eq(99L), eq(10L)))
                .thenThrow(new RentalRequestNotFoundException());

        mockMvc.perform(post("/api/fm/rental-requests/99/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":10}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveUnknownUnitReturns404() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.approve(eq(1L), eq(10L)))
                .thenThrow(new StorageUnitNotFoundException());

        mockMvc.perform(post("/api/fm/rental-requests/1/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":10}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy khoang"));
    }

    @Test
    void crossFacilityReturns403() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.approve(eq(1L), eq(10L)))
                .thenThrow(new ForbiddenException("Bạn không được gán vào cơ sở này"));

        mockMvc.perform(post("/api/fm/rental-requests/1/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không được gán vào cơ sở này"));
    }

    @Test
    void anonymousApproveReturns401() throws Exception {
        mockMvc.perform(post("/api/fm/rental-requests/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":10}"))
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
