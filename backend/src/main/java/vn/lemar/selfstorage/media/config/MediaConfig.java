package vn.lemar.selfstorage.media.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình module media — tự quản, không sửa file dùng chung (theo precedent {@code PaymentConfig}).
 *
 * <p>Giới hạn multipart đặt ở {@code application.yml} (`spring.servlet.multipart`) để không
 * override ngầm cấu hình chung của app.
 */
@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class MediaConfig {

    @Bean
    Cloudinary cloudinary(CloudinaryProperties properties) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.name(),
                "api_key", properties.key(),
                "api_secret", properties.secret(),
                "secure", true));
    }
}
