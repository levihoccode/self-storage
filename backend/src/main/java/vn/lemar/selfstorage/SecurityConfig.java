package vn.lemar.selfstorage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import vn.lemar.selfstorage.identity.application.IdentityWebSecuritySupport;

/**
 * Khung phan quyen theo issue #15 muc 2 va muc 7.
 *
 * <p>Nam nhom route theo role da duoc khai bao san nhung hien de {@code permitAll},
 * vi bo khung chua co co che dang nhap day du theo role. Khi can bat authorization
 * thi thay {@code permitAll} bang {@code hasRole} tuong ung va bat them
 * facility-scope check o tang method.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    IdentityWebSecuritySupport identityWebSecuritySupport) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // Siết tối thiểu: cần token hợp lệ (thiếu token -> 401).
                        .requestMatchers("/api/me/**").authenticated()
                        // TODO(identity): doi sang hasRole khi bat authorization
                        .requestMatchers("/api/customer/**").permitAll()
                        .requestMatchers("/api/staff/**").permitAll()
                        .requestMatchers("/api/fm/**").permitAll()
                        .requestMatchers("/api/bom/**").permitAll()
                        .requestMatchers("/api/admin/**").permitAll()
                        .anyRequest().permitAll());
        identityWebSecuritySupport.registerJwtFilter(http);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}