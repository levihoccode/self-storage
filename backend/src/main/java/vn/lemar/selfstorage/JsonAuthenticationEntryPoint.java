package vn.lemar.selfstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 401 kèm body JSON chung theo quy ước {@link ApiError}; chi tiết lỗi chỉ ghi log.
 * Header {@code WWW-Authenticate} giữ chuẩn RFC 6750 nhưng bỏ {@code error_description}.
 */
public final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOG = LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);

    private static final String UNAUTHENTICATED_MESSAGE = "Bạn cần đăng nhập để tiếp tục.";
    private static final String BEARER_CHALLENGE = "Bearer";
    private static final String INVALID_TOKEN_CHALLENGE = "Bearer error=\"invalid_token\"";

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        LOG.debug("Unauthenticated request: {}", authException.getMessage());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge(authException));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiError.now(UNAUTHENTICATED_MESSAGE));
    }

    private static String challenge(AuthenticationException authException) {
        return authException instanceof OAuth2AuthenticationException ? INVALID_TOKEN_CHALLENGE : BEARER_CHALLENGE;
    }
}
