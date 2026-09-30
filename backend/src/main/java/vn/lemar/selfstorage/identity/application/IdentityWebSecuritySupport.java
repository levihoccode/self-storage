package vn.lemar.selfstorage.identity.application;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

/**
 * Gắn JWT filter vào chuỗi Spring Security — type duy nhất root {@code SecurityConfig} được inject.
 */
@Component
public class IdentityWebSecuritySupport {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public IdentityWebSecuritySupport(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    public void registerJwtFilter(HttpSecurity http) throws Exception {
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
