package vn.lemar.selfstorage.identity.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class JwtConfiguration {

    private static final String SECRET_KEY_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_LENGTH = 32;

    @Bean
    SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String encodedSecret) {
        byte[] secret = Base64.getDecoder().decode(encodedSecret);
        if (secret.length < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
        }
        return new SecretKeySpec(secret, SECRET_KEY_ALGORITHM);
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("self-storage"));
        return decoder;
    }
}