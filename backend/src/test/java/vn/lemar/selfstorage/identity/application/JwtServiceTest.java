package vn.lemar.selfstorage.identity.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes();

    @Test
    void issueAccessTokenSignsEmailClaimAndExpiryWithoutRole() {
        SecretKey secretKey = new SecretKeySpec(SECRET, "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtService jwtService = new JwtService(encoder, Duration.ofMinutes(15));

        Role role = mock(Role.class);
        when(role.getName()).thenReturn("CUSTOMER");
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(42L);
        when(account.getEmail()).thenReturn("customer@example.com");
        when(account.getRole()).thenReturn(role);

        Jwt token = decoder.decode(jwtService.issueAccessToken(account));

        assertThat(token.getClaimAsString("email")).isEqualTo("customer@example.com");
        assertThat(token.getClaims()).doesNotContainKey("role");
        assertThat(token.getClaims()).doesNotContainKey("sub");
        assertThat(token.getClaimAsString("iss")).isEqualTo("self-storage");
        assertThat(token.getExpiresAt()).isEqualTo(token.getIssuedAt().plus(Duration.ofMinutes(15)));
    }

    @Test
    void constructorRejectsNonPositiveTtl() {
        JwtEncoder encoder = mock(JwtEncoder.class);

        assertThatThrownBy(() -> new JwtService(encoder, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService(encoder, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}