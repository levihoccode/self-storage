package vn.lemar.selfstorage.scheduler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật {@code @Scheduled} cho các job trong module scheduler. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
