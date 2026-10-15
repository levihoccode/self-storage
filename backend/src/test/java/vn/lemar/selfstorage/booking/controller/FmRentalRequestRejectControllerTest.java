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
import vn.lemar.selfstorage.booking.application.dto.RejectRentalRequestResponse;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestAlreadyHandledException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestValidationException;
import vn.lemar.selfstorage.booking.domain.RentalRequestRejectReason;
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

@WebMvcTest(controllers = {FmRentalRequestRejectController.class, AuthController.class})
@Import({SecurityConfig.class, JwtConfiguration.class, AccountJwtAuthenticationConverter.class,
        BookingExceptionHandler.class, AuthExceptionHandler.class})
@TestPropertySource(properties = "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class FmRentalRequestRejectControllerTest {

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
    void rejectReturnsRejected() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        Instant now = Instant.now();
        when(rentalRequestService.reject(eq(1L), eq(RentalRequestRejectReason.UNIT_UNAVAILABLE),
                eq(null))).thenReturn(
                        new RejectRentalRequestResponse(1L, "Rejected", "UNIT_UNAVAILABLE", now));

        mockMvc.perform(post("/api/fm/rental-requests/1/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"UNIT_UNAVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã từ chối yêu cầu đặt kho"))
                .andExpect(jsonPath("$.data.status").value("Rejected"))
                .andExpect(jsonPath("$.data.rejectReason").value("UNIT_UNAVAILABLE"));
    }

    @Test
    void rejectOtherWithoutNoteReturns400() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.reject(eq(1L), eq(RentalRequestRejectReason.OTHER), eq(null)))
                .thenThrow(new RentalRequestValidationException(
                        "Ghi chú bắt buộc khi lý do là Khác"));

        mockMvc.perform(post("/api/fm/rental-requests/1/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ghi chú bắt buộc khi lý do là Khác"));
    }

    @Test
    void rejectHandledTwiceReturns409() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.reject(eq(1L), eq(RentalRequestRejectReason.UNIT_MISMATCH),
                eq("note"))).thenThrow(new RentalRequestAlreadyHandledException());

        mockMvc.perform(post("/api/fm/rental-requests/1/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"UNIT_MISMATCH\",\"rejectNote\":\"note\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Yêu cầu đã được xử lý bởi FM khác"));
    }

    @Test
    void rejectUnknownRequestReturns404() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.reject(eq(99L), eq(RentalRequestRejectReason.CUSTOMER_REQUEST),
                eq(null))).thenThrow(new RentalRequestNotFoundException());

        mockMvc.perform(post("/api/fm/rental-requests/99/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossFacilityReturns403() throws Exception {
        authenticate("fm@lemar.vn", "FM");
        when(rentalRequestService.reject(eq(1L), eq(RentalRequestRejectReason.UNIT_UNAVAILABLE),
                eq(null))).thenThrow(new ForbiddenException("Bạn không được gán vào cơ sở này"));

        mockMvc.perform(post("/api/fm/rental-requests/1/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@lemar.vn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"UNIT_UNAVAILABLE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không được gán vào cơ sở này"));
    }

    @Test
    void anonymousRejectReturns401() throws Exception {
        mockMvc.perform(post("/api/fm/rental-requests/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"UNIT_UNAVAILABLE\"}"))
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
