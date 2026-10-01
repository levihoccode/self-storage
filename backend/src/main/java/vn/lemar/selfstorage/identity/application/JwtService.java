package vn.lemar.selfstorage.identity.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import vn.lemar.selfstorage.identity.domain.Account;

@Service
public class JwtService {

    private static final String ISSUER = "self-storage";

    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenTtl;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl) {
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
    }

    public String issueAccessToken(Account account) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(accessTokenTtl))
                .subject(account.getId().toString())
                .claim("email", account.getEmail())
                .claim("role", account.getRole().getName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}