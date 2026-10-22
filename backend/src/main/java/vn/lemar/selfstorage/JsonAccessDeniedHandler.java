package vn.lemar.selfstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import vn.lemar.selfstorage.identity.application.AccountJwtAuthenticationConverter;

/**
 * 403 kèm body JSON theo quy ước {@link ApiError}. Account {@code BANNED} không được converter cấp
 * authority nào; account {@code ACTIVE} chưa xác minh được gắn
 * {@link AccountJwtAuthenticationConverter#ACCOUNT_UNVERIFIED_AUTHORITY} — phân biệt được với token
 * sai role.
 */
public final class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private static final String BANNED_MESSAGE = "Tài khoản đã bị chặn";
    private static final String UNVERIFIED_MESSAGE = "Tài khoản chưa xác minh email";
    private static final String DENIED_MESSAGE = "Bạn không có quyền truy cập";

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiError.now(message()));
    }

    private static String message() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return DENIED_MESSAGE;
        }
        boolean unverified = authentication.getAuthorities().stream().anyMatch(authority ->
                AccountJwtAuthenticationConverter.ACCOUNT_UNVERIFIED_AUTHORITY.equals(authority.getAuthority()));
        if (unverified) {
            return UNVERIFIED_MESSAGE;
        }
        return authentication.getAuthorities().isEmpty() ? BANNED_MESSAGE : DENIED_MESSAGE;
    }
}
