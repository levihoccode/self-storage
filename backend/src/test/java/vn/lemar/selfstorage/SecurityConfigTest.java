package vn.lemar.selfstorage;

import java.time.Instant;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.identity.application.AccountJwtAuthenticationConverter;
import vn.lemar.selfstorage.identity.application.AuthService;
import vn.lemar.selfstorage.identity.application.dto.MeResponse;
import vn.lemar.selfstorage.identity.config.JwtConfiguration;
import vn.lemar.selfstorage.identity.controller.AuthController;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.AccountStatus;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.dto.IpnResponse;
import vn.lemar.selfstorage.payment.controller.VnPayCallbackController;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        SecurityConfigTest.ProbeController.class,
        AuthController.class,
        VnPayCallbackController.class
})
@Import({
        SecurityConfig.class,
        JwtConfiguration.class,
        AccountJwtAuthenticationConverter.class,
        SecurityConfigTest.ProbeController.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private AuthService authService;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private PaymentGateway paymentGateway;

    @Test
    void protectedRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/customer/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void activeDatabaseRoleCanAccessMatchingRoute() throws Exception {
        Account customer = account("CUSTOMER", AccountStatus.ACTIVE);
        when(accountRepository.findWithRoleByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));

        mockMvc.perform(get("/api/customer/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    void databaseRoleCannotAccessDifferentRoleRoute() throws Exception {
        Account fm = account("FM", AccountStatus.ACTIVE);
        when(accountRepository.findWithRoleByEmail("fm@example.com"))
                .thenReturn(Optional.of(fm));

        mockMvc.perform(get("/api/customer/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("fm@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập"));
    }

    @Test
    void accountStatusIsReadAgainFromDatabaseForEachRequest() throws Exception {
        Account banned = account("CUSTOMER", AccountStatus.BANNED);
        when(accountRepository.findWithRoleByEmail("customer@example.com"))
                .thenReturn(Optional.of(banned));

        mockMvc.perform(get("/api/internal/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản đã bị chặn"));
    }

    @Test
    void jwtForDeletedAccountIsRejected() throws Exception {
        when(accountRepository.findWithRoleByEmail("deleted@example.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/customer/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("deleted@example.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentAccountForActiveToken() throws Exception {
        Account customer = account("CUSTOMER", AccountStatus.ACTIVE);
        when(accountRepository.findWithRoleByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));
        when(authService.me("customer@example.com"))
                .thenReturn(new MeResponse("customer@example.com", "CUSTOMER"));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy thông tin tài khoản thành công"))
                .andExpect(jsonPath("$.data.email").value("customer@example.com"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    @Test
    void meRejectsBannedAccountToken() throws Exception {
        Account banned = account("CUSTOMER", AccountStatus.BANNED);
        when(accountRepository.findWithRoleByEmail("customer@example.com"))
                .thenReturn(Optional.of(banned));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản đã bị chặn"));
    }

    /**
     * VNPay gọi IPN từ server của họ, không có JWT và sẽ không bao giờ có. Nếu route này rơi vào
     * dòng vét `/api/**` thì VNPay nhận 401, coi như merchant không xác nhận, và toàn bộ A6 chết.
     * Test này là chốt chặn để lần sau có ai siết security thì CI đỏ chứ không phải tiền đi lạc.
     */
    @Test
    void vnpayIpnPhaiGoiDuocKhiKhongCoToken() throws Exception {
        when(paymentService.handleIpn(anyMap())).thenReturn(IpnResponse.success());

        mockMvc.perform(get("/api/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "TXN1")
                        .param("vnp_SecureHash", "deadbeef"))
                .andExpect(status().isOk())
                // Tên key phải đúng chữ hoa VNPay đòi — kiểm luôn qua cả tầng MVC.
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
    }

    /** Return URL là redirect trình duyệt từ VNPay nên cũng không mang Authorization. */
    @Test
    void vnpayReturnPhaiGoiDuocKhiKhongCoToken() throws Exception {
        when(paymentGateway.verifySignature(anyMap())).thenReturn(true);

        mockMvc.perform(get("/api/payments/vnpay/return")
                        .param("vnp_TxnRef", "TXN1")
                        .param("vnp_ResponseCode", "00"))
                .andExpect(status().isOk());
    }

    private Account account(String roleName, AccountStatus status) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        Account account = mock(Account.class);
        when(account.getEmail()).thenReturn(roleName.toLowerCase() + "@example.com");
        when(account.getRole()).thenReturn(role);
        when(account.getStatus()).thenReturn(status);
        when(account.isLoginAllowed()).thenReturn(status == AccountStatus.ACTIVE);
        return account;
    }

    private String tokenFor(String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self-storage")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("email", email)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/customer/probe")
        String customerProbe() {
            return "customer";
        }

        @GetMapping("/api/internal/probe")
        String internalProbe() {
            return "internal";
        }
    }
}