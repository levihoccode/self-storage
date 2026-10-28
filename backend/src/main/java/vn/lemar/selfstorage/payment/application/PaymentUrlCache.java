package vn.lemar.selfstorage.payment.application;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache URL thanh toán theo invoice, TTL đúng bằng cửa sổ phiên VNPay.
 *
 * <p>URL là dữ liệu tạm: mất cache không làm sai trạng thái tiền, chỉ khiến khách không quay lại
 * được phiên cũ. Vì vậy mọi lỗi Redis đều bị nuốt và log — DB vẫn là nguồn của khóa/phiên.
 */
@Component
public class PaymentUrlCache {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentUrlCache.class);
    private static final String KEY_PREFIX = "payment:url:";

    private final StringRedisTemplate redis;

    public PaymentUrlCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Ghi URL của phiên đang mở; lỗi Redis không được làm hỏng việc mở phiên. */
    public void put(Long invoiceId, String paymentUrl) {
        try {
            redis.opsForValue().set(key(invoiceId), paymentUrl, PaymentSession.WINDOW);
        } catch (RuntimeException e) {
            LOG.warn("Không cache được URL thanh toán cho hóa đơn {}: {}", invoiceId, e.getMessage());
        }
    }

    /** URL còn hạn của phiên cũ, nếu cache còn. */
    public Optional<String> get(Long invoiceId) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key(invoiceId)));
        } catch (RuntimeException e) {
            LOG.warn("Không đọc được URL thanh toán cho hóa đơn {}: {}", invoiceId, e.getMessage());
            return Optional.empty();
        }
    }

    private static String key(Long invoiceId) {
        return KEY_PREFIX + invoiceId;
    }
}
