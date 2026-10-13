package vn.lemar.selfstorage.media.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình module media — tự quản, không sửa file dùng chung (theo precedent {@code PaymentConfig}).
 */
@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class MediaConfig {

    /** Servlet nhận tới 12MB để file 10–12MB tới được validator của module và trả 400 có message. */
    private static final long MULTIPART_LIMIT_BYTES = 12L * 1024 * 1024;

    @Bean
    Cloudinary cloudinary(CloudinaryProperties properties) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.name(),
                "api_key", properties.key(),
                "api_secret", properties.secret(),
                "secure", true));
    }

    @Bean
    MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement((String) null, MULTIPART_LIMIT_BYTES, MULTIPART_LIMIT_BYTES, 0);
    }
}
