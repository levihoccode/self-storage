package vn.lemar.selfstorage.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật scheduler cho job đối soát thanh toán. Một instance nên chưa cần ShedLock. */
@Configuration
@EnableScheduling
public class PaymentSchedulingConfig {
}
