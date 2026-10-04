package vn.lemar.selfstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 401 kèm body JSON theo quy ước {@link ApiError}, giữ header {@code WWW-Authenticate} (RFC 6750)
 * mà resource server mặc định không kèm body.
 */
public final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        delegate.commence(request, response, authException);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiError.now(messageFor(authException)));
    }

    private static String messageFor(AuthenticationException authException) {
        if (authException instanceof OAuth2AuthenticationException oauth2) {
            String description = oauth2.getError().getDescription();
            if (description != null) {
                return description;
            }
        }
        return authException.getMessage();
    }
}
