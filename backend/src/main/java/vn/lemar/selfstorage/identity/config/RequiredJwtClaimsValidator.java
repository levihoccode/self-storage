package vn.lemar.selfstorage.identity.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Requires the claims this backend actually consumes: {@code exp} (validity window — the default
 * timestamp validator only checks it when present) and {@code email} (account lookup in
 * AccountJwtAuthenticationConverter). A token missing either claim is rejected at decode time, so
 * every producer must set them: one token shape, one source of truth.
 */
final class RequiredJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> missing = new ArrayList<>(2);
        if (jwt.getExpiresAt() == null) {
            missing.add("exp");
        }
        if (jwt.getClaimAsString("email") == null) {
            missing.add("email");
        }
        if (missing.isEmpty()) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "JWT is missing required claims: " + String.join(", ", missing),
                "https://tools.ietf.org/html/rfc6750#section-3.1");
        return OAuth2TokenValidatorResult.failure(error);
    }
}
