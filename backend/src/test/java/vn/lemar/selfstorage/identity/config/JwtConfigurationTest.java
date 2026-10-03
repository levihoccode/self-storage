package vn.lemar.selfstorage.identity.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTest {

    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    void rejectsSecretShorterThan32Bytes() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> configuration.jwtSecretKey(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void rejectsNonBase64Secret() {
        assertThatThrownBy(() -> configuration.jwtSecretKey("not-base64!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsHmacSha256KeyFromValidSecret() {
        String secret = Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

        SecretKey key = configuration.jwtSecretKey(secret);

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
