package vn.lemar.selfstorage;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
import vn.lemar.selfstorage.identity.config.JwtConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, JwtConfiguration.class, SecurityConfigTest.ProbeController.class})
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void protectedRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/customer/probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void matchingRoleCanAccessRoute() throws Exception {
        mockMvc.perform(get("/api/customer/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void differentRoleCannotAccessRoute() throws Exception {
        mockMvc.perform(get("/api/customer/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("FM")))
                .andExpect(status().isForbidden());
    }

    private String tokenFor(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self-storage")
                .subject("42")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("role", role)
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
    }
}