package vn.lemar.selfstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ACTIVE_ACCOUNT_AUTHORITY = "ACCOUNT_ACTIVE";

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            Converter<Jwt, JwtAuthenticationToken> authenticationConverter,
            ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/me").hasAuthority(ACTIVE_ACCOUNT_AUTHORITY)
                        .requestMatchers("/api/health", "/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/staff/**").hasRole("FS")
                        .requestMatchers("/api/fm/**").hasRole("FM")
                        .requestMatchers("/api/bom/**").hasRole("BOM")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAuthority(ACTIVE_ACCOUNT_AUTHORITY)
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder(
            @Value("${security.password.bcrypt-strength:10}") int bcryptStrength) {
        return new BCryptPasswordEncoder(bcryptStrength);
    }
}
