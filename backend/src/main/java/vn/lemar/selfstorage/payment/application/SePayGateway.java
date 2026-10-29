package vn.lemar.selfstorage.payment.application;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.config.SePayProperties;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

/**
 * Adapter SePay: QR chuyển khoản VietQR + webhook biến động số dư.
 *
 * <p>SePay không redirect khách sang trang cổng; {@code paymentUrl} là URL ảnh VietQR để FE hiển
 * thị. Xác nhận thanh toán đến từ webhook (HMAC-SHA256 trên raw body).
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "sepay")
public class SePayGateway implements PaymentGateway {

    private static final Logger LOG = LoggerFactory.getLogger(SePayGateway.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TRANSACTION_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_SKEW_SECONDS = 300;

    private final SePayProperties properties;

    public SePayGateway(SePayProperties properties) {
        this.properties = properties;
    }

    @Override
    public String provider() {
        return "sepay";
    }

    @Override
    public String buildPaymentUrl(PaymentRequest request) {
        return buildPaymentUrl(request, Instant.now());
    }

    @Override
    public String buildPaymentUrl(PaymentRequest request, Instant createdAt) {
        requireConfig();
        String amount = request.amount().toBigIntegerExact().toString();
        String description = URLEncoder.encode(request.txnRef(), StandardCharsets.UTF_8);
        return properties.qrBaseUrl() + "?acc=" + properties.accountNumber()
                + "&bank=" + properties.bank()
                + "&amount=" + amount
                + "&des=" + description
                + "&template=compact";
    }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        return false;
    }

    @Override
    public GatewayNotification parseNotification(Map<String, String> payload) {
        String transferType = payload.get("transferType");
        String txnRef = firstNonBlank(payload.get("code"), payload.get("content"));
        if (txnRef == null) {
            throw new IllegalArgumentException("Webhook SePay thiếu code/content");
        }
        PaymentStatus status = "in".equalsIgnoreCase(transferType)
                ? PaymentStatus.SUCCESS : PaymentStatus.PENDING;
        BigDecimal amount = parseAmount(payload.get("transferAmount"));
        Instant paidAt = parseTransactionDate(payload.get("transactionDate"));
        return new GatewayNotification(txnRef, status, amount, paidAt,
                payload.get("referenceCode"), payload.toString());
    }

    /**
     * Xác thực HMAC-SHA256 của SePay: {@code sha256=HMAC(timestamp + "." + rawBody, secret)}.
     * Timestamp lệch quá 5 phút bị từ chối để chống replay.
     */
    @Override
    public boolean verifyWebhook(String rawBody, String signature, String timestamp) {
        if (blank(properties.webhookSecret()) || blank(signature) || blank(timestamp)) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(Instant.now().getEpochSecond() - ts) > MAX_SKEW_SECONDS) {
            return false;
        }
        String expected = "sha256=" + hmac(timestamp + "." + rawBody, properties.webhookSecret());
        return constantTimeEquals(expected, signature);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant parseTransactionDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(raw, TRANSACTION_DATE).atZone(ZONE).toInstant();
        } catch (RuntimeException e) {
            LOG.warn("SePay transactionDate không đọc được: {}", raw);
            return Instant.now();
        }
    }

    private void requireConfig() {
        if (blank(properties.bank()) || blank(properties.accountNumber())
                || blank(properties.qrBaseUrl())) {
            throw new IllegalStateException("Thiếu cấu hình SePay (bank/account-number/qr-base-url)");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String hmac(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không ký được HMAC-SHA256", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String received) {
        return expected != null && received != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }
}
