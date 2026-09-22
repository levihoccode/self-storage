package vn.lemar.selfstorage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Khung phân quyền theo issue #15 mục 2 và mục 7.
 *
 * <p>Năm nhóm route theo role đã được khai báo sẵn nhưng hiện để {@code permitAll},
 * vì bộ khung chưa có cơ chế đăng nhập. Khi module {@code identity} làm xong phần
 * authentication thì thay {@code permitAll} bằng {@code hasRole} tương ứng và bật
 * thêm facility-scope check ở tầng method.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        // TODO(identity): đổi sang hasRole khi có authentication
                        .requestMatchers("/api/customer/**").permitAll()
                        .requestMatchers("/api/staff/**").permitAll()
                        .requestMatchers("/api/fm/**").permitAll()
                        .requestMatchers("/api/bom/**").permitAll()
                        .requestMatchers("/api/admin/**").permitAll()
                        .anyRequest().permitAll());
        return http.build();
    }
}
