package vn.lemar.selfstorage.payment.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.application.dto.TransactionQuery;
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult;
import vn.lemar.selfstorage.payment.config.PayOsProperties;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

/**
 * Adapter payOS (VietQR chuyển khoản ngân hàng).
 *
 * <p>Khác VNPay/ZaloPay: mã đơn ({@code orderCode}) phải là số, link có hạn bằng {@code expiredAt}
 * (Unix giây), webhook chỉ cần HTTP 2xx. payOS không có sandbox và không có API hoàn tiền.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "payos")
public class PayOsGateway implements PaymentGateway {

    private static final Logger LOG = LoggerFactory.getLogger(PayOsGateway.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter WEBHOOK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SUCCESS_CODE = "00";
    /** {@code code} của tra cứu link khi không có {@code orderCode} này. */
    private static final String NOT_FOUND_CODE = "101";
    /** Tài khoản ngân hàng chưa liên kết qua payOS chỉ nhận nội dung chuyển khoản 9 ký tự. */
    private static final int MAX_DESCRIPTION = 9;

    private final PayOsProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient http = RestClient.builder()
            .requestFactory(requestFactory())
            .build();

    public PayOsGateway(PayOsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return "payos";
    }

    /**
     * {@code orderCode} = mốc tạo (ms) × 100 + 2 chữ số ngẫu nhiên — 15 chữ số, dưới giới hạn số
     * nguyên an toàn của JSON. Cột {@code vnp_txn_ref} UNIQUE chặn trường hợp trùng hiếm hoi.
     */
    @Override
    public String createTxnRef(String baseRef, Instant createdAt) {
        return String.valueOf(createdAt.toEpochMilli() * 100 + ThreadLocalRandom.current().nextInt(100));
    }

    @Override
    public String buildPaymentUrl(PaymentRequest request) {
        return buildPaymentUrl(request, Instant.now());
    }

    @Override
    public String buildPaymentUrl(PaymentRequest request, Instant createdAt) {
        requireConfig();
        long orderCode = Long.parseLong(request.txnRef());
        long amount = request.amount().toBigIntegerExact().longValueExact();
        String description = description(request.txnRef());
        Map<String, Object> body = new TreeMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", description);
        body.put("returnUrl", properties.returnUrl());
        body.put("cancelUrl", properties.cancelUrl());
        // Link hết hạn đúng lúc khoá phiên của hệ thống hết (15'), không dựa vào mặc định của cổng.
        body.put("expiredAt", createdAt.plus(PaymentSession.WINDOW).getEpochSecond());
        body.put("signature", hmac(createSignatureData(amount, properties.cancelUrl(), description,
                orderCode, properties.returnUrl()), properties.checksumKey()));

        Map<String, Object> response = call(http.post()
                .uri(properties.baseUrl() + "/v2/payment-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
        Map<String, Object> data = dataOf(response);
        if (!SUCCESS_CODE.equals(String.valueOf(response.get("code"))) || data == null
                || data.get("checkoutUrl") == null) {
            throw new IllegalStateException("payOS tạo link thất bại: " + response.get("desc"));
        }
        // Không log URL: mở được trang thanh toán của khách.
        LOG.info("Đã tạo link payOS {}", orderCode);
        return String.valueOf(data.get("checkoutUrl"));
    }

    /** Chuỗi ký request tạo link — đúng 5 field, theo thứ tự alphabet (docs payOS). */
    static String createSignatureData(long amount, String cancelUrl, String description, long orderCode,
                                      String returnUrl) {
        return "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description
                + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;
    }

    /** Nội dung chuyển khoản ≤ 9 ký tự: {@code SS} + 7 chữ số cuối của {@code orderCode}. */
    static String description(String orderCode) {
        String digits = orderCode.length() <= MAX_DESCRIPTION - 2
                ? orderCode : orderCode.substring(orderCode.length() - (MAX_DESCRIPTION - 2));
        return "SS" + digits;
    }

    /**
     * {@code GET /v2/payment-requests/{orderCode}} — job đối soát dùng chung. {@code PAID} là thành
     * công; {@code CANCELLED}/{@code EXPIRED}/{@code FAILED} là thất bại; {@code PENDING},
     * {@code PROCESSING}, {@code UNDERPAID} (chuyển thiếu) là chưa xong.
     */
    @Override
    public TransactionQueryResult queryTransaction(TransactionQuery query) {
        requireConfig();
        return toQueryResult(call(http.get()
                .uri(properties.baseUrl() + "/v2/payment-requests/{id}", query.txnRef())));
    }

    /**
     * Chuẩn hoá response tra cứu. {@code code = 101} ("Mã thanh toán không tồn tại", HTTP 200) là
     * không có link; mọi {@code code} khác {@code 00} còn lại là {@code UNKNOWN} — giao dịch giữ Pending.
     */
    TransactionQueryResult toQueryResult(Map<String, Object> response) {
        String raw = toJson(response);
        String message = String.valueOf(response.getOrDefault("desc", ""));
        Map<String, Object> data = dataOf(response);
        if (NOT_FOUND_CODE.equals(String.valueOf(response.get("code")))) {
            return result(TransactionQueryResult.Outcome.NOT_FOUND, null, null, null, message, raw);
        }
        if (!SUCCESS_CODE.equals(String.valueOf(response.get("code"))) || data == null) {
            return result(TransactionQueryResult.Outcome.UNKNOWN, null, null, null, message, raw);
        }
        String status = String.valueOf(data.get("status"));
        Map<String, Object> lastTransaction = lastTransaction(data.get("transactions"));
        String reference = lastTransaction == null || lastTransaction.get("reference") == null
                ? null : String.valueOf(lastTransaction.get("reference"));
        return switch (status) {
            case "PAID" -> result(TransactionQueryResult.Outcome.SUCCESS, reference,
                    lastTransaction == null ? null : paidAt(lastTransaction.get("transactionDateTime")),
                    decimal(data.get("amountPaid")), status, raw);
            case "CANCELLED", "EXPIRED", "FAILED" -> result(TransactionQueryResult.Outcome.FAILED, reference,
                    null, null, status, raw);
            case "PENDING", "PROCESSING", "UNDERPAID" -> result(TransactionQueryResult.Outcome.PENDING,
                    reference, null, null, status, raw);
            default -> result(TransactionQueryResult.Outcome.UNKNOWN, reference, null, null, status, raw);
        };
    }

    private static TransactionQueryResult result(TransactionQueryResult.Outcome outcome, String transactionNo,
                                                 Instant paidAt, BigDecimal amount, String message,
                                                 String raw) {
        return new TransactionQueryResult(outcome, transactionNo, paidAt, amount, message, raw);
    }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        String data = params.get("data");
        String signature = params.get("signature");
        return data != null && signature != null
                && constantTimeEquals(hmac(webhookSignatureData(readJson(data)), properties.checksumKey()),
                        signature);
    }

    /**
     * Webhook payOS: {@code payload.data} là object {@code data} dạng JSON, {@code payload.signature}
     * là chữ ký. Chữ ký chỉ phủ {@code data}; {@code data.code = 00} là thanh toán thành công.
     */
    @Override
    public GatewayNotification parseNotification(Map<String, String> payload) {
        String rawData = payload.get("data");
        String signature = payload.get("signature");
        if (rawData == null || signature == null) {
            throw new IllegalArgumentException("Webhook payOS thiếu data/signature");
        }
        requireConfig();
        Map<String, Object> data = readJson(rawData);
        if (!constantTimeEquals(hmac(webhookSignatureData(data), properties.checksumKey()), signature)) {
            throw new IllegalArgumentException("Webhook payOS sai chữ ký");
        }
        Object orderCode = data.get("orderCode");
        BigDecimal amount = decimal(data.get("amount"));
        if (orderCode == null || amount == null) {
            throw new IllegalArgumentException("Webhook payOS thiếu orderCode/amount");
        }
        PaymentStatus status = SUCCESS_CODE.equals(String.valueOf(data.get("code")))
                ? PaymentStatus.SUCCESS : null;
        String reference = data.get("reference") == null ? null : String.valueOf(data.get("reference"));
        return new GatewayNotification(String.valueOf(orderCode), status, amount,
                paidAt(data.get("transactionDateTime")), reference, rawData);
    }

    /**
     * Chuỗi ký webhook: các key của {@code data} theo thứ tự alphabet, {@code key=value} nối bằng
     * {@code &}; null thành chuỗi rỗng, mảng thành JSON với key từng phần tử đã sắp (docs payOS).
     */
    String webhookSignatureData(Map<String, Object> data) {
        StringBuilder query = new StringBuilder();
        new TreeMap<>(data).forEach((key, value) -> {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(key).append('=').append(signatureValue(value));
        });
        return query.toString();
    }

    @SuppressWarnings("unchecked")
    private String signatureValue(Object value) {
        if (value == null || "null".equals(value) || "undefined".equals(value)) {
            return "";
        }
        if (value instanceof List<?> list) {
            List<Object> sorted = list.stream()
                    .map(item -> item instanceof Map<?, ?> map
                            ? (Object) new TreeMap<>((Map<String, Object>) map) : item)
                    .toList();
            return toJson(sorted);
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(Map<String, Object> response) {
        return response.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> lastTransaction(Object transactions) {
        if (transactions instanceof List<?> list && !list.isEmpty()
                && list.get(list.size() - 1) instanceof Map<?, ?> last) {
            return (Map<String, Object>) last;
        }
        return null;
    }

    /**
     * Webhook trả {@code yyyy-MM-dd HH:mm:ss} theo giờ Việt Nam; tra cứu có thể trả ISO-8601. Không
     * đọc được thì để null — service dùng thời điểm ghi nhận.
     */
    static Instant paidAt(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        try {
            return LocalDateTime.parse(value, WEBHOOK_TIME).atZone(ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
            // thử ISO-8601 bên dưới
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // thử ISO không có offset
        }
        try {
            return LocalDateTime.parse(value).atZone(ZONE).toInstant();
        } catch (DateTimeParseException e) {
            LOG.warn("Không đọc được thời điểm thanh toán payOS: {}", value);
            return null;
        }
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("payOS amount không hợp lệ", e);
        }
    }

    private Map<String, Object> call(RestClient.RequestHeadersSpec<?> request) {
        // Lỗi 4xx/5xx của payOS vẫn có body {code, desc} — đọc body thay vì ném exception HTTP.
        Map<String, Object> response = request
                .header("x-client-id", properties.clientId())
                .header("x-api-key", properties.apiKey())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                })
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null) {
            throw new IllegalStateException("payOS trả body rỗng");
        }
        return response;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> readJson(String data) {
        try {
            return objectMapper.readValue(data, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được data payOS", e);
        }
    }

    private void requireConfig() {
        if (blank(properties.clientId()) || blank(properties.apiKey()) || blank(properties.checksumKey())
                || blank(properties.baseUrl()) || blank(properties.returnUrl())
                || blank(properties.cancelUrl())) {
            throw new IllegalStateException(
                    "Thiếu cấu hình payOS (client-id/api-key/checksum-key/base-url/return-url/cancel-url)");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String hmac(String data, String key) {
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
        if (expected == null || received == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    private static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return factory;
    }
}
