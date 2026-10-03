package vn.lemar.selfstorage.notification.application;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Bật {@code @Async} để gửi email không chặn request của khách. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
