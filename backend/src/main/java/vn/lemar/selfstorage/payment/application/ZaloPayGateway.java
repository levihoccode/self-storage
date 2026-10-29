package vn.lemar.selfstorage.payment.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.application.dto.TransactionQuery;
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult;
import vn.lemar.selfstorage.payment.config.ZaloPayProperties;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

/**
 * Adapter ZaloPay (sandbox).
 *
 * <p>Khác VNPay: tạo đơn là một HTTP call đồng bộ, {@code app_trans_id} phải có tiền tố
 * {@code yyMMdd} theo giờ Việt Nam, số tiền là số nguyên VND, callback là JSON {@code {data, mac}}.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "zalopay")
public class ZaloPayGateway implements PaymentGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ZaloPayGateway.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String APP_USER = "self-storage";
    private static final int MAX_TXN_REF = 40;
    /** {@code sub_return_code} của {@code /v2/query}: không có đơn với {@code app_trans_id} này. */
    private static final int ORDER_NOT_EXIST = -101;

    private final ZaloPayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient http = RestClient.builder()
            .requestFactory(requestFactory())
            .build();

    public ZaloPayGateway(ZaloPayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return "zalopay";
    }

    @Override
    public String createTxnRef(String baseRef, Instant createdAt) {
        String ref = DATE.format(createdAt.atZone(ZONE)) + "_" + baseRef;
        return ref.length() <= MAX_TXN_REF ? ref : ref.substring(0, MAX_TXN_REF);
    }

    @Override
    public String buildPaymentUrl(PaymentRequest request) {
        return buildPaymentUrl(request, Instant.now());
    }

    @Override
    public String buildPaymentUrl(PaymentRequest request, Instant createdAt) {
        requireConfig();
        String appTime = String.valueOf(createdAt.toEpochMilli());
        String amount = request.amount().toBigIntegerExact().toString();
        String item = "[]";
        String embedData = embedData();
        String data = String.join("|", properties.appId(), request.txnRef(), APP_USER, amount,
                appTime, embedData, item);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app_id", Long.parseLong(properties.appId()));
        body.put("app_user", APP_USER);
        body.put("app_trans_id", request.txnRef());
        body.put("app_time", createdAt.toEpochMilli());
        body.put("amount", Long.parseLong(amount));
        body.put("item", item);
        body.put("embed_data", embedData);
        body.put("description", truncate(request.orderInfo(), 256));
        // Đơn hết hạn đúng lúc khoá phiên của hệ thống hết (15'), không dựa vào mặc định của cổng.
        body.put("expire_duration_seconds", PaymentSession.WINDOW.toSeconds());
        body.put("mac", hmac(data, properties.key1()));
        if (properties.callbackUrl() != null && !properties.callbackUrl().isBlank()) {
            body.put("callback_url", properties.callbackUrl());
        }

        Map<String, Object> response = post(properties.createUrl(), body);
        if (!"1".equals(String.valueOf(response.get("return_code")))) {
            throw new IllegalStateException("ZaloPay create thất bại: " + response.get("return_message"));
        }
        // qr_code là VietQR/NAPAS (MB Bank, app ngân hàng quét được); order_url là QR ZaloPay.
        // App sandbox 553 trả QR thay vì URL, nên dựng openinapp URL từ order_token để FE redirect.
        Object orderUrl = response.get("order_url");
        Object orderToken = response.get("order_token");
        String appUrl = orderUrl == null ? null : orderUrl.toString();
        // Không log URL/token: chứa mã giao dịch có thể dùng để mở trang thanh toán.
        if (appUrl != null && appUrl.startsWith("http")) {
            LOG.info("Đã tạo đơn ZaloPay {} (order_url)", request.txnRef());
            return appUrl;
        }
        if (orderToken != null && !orderToken.toString().isBlank()) {
            String tokenJson = "{\"zptranstoken\":\"" + orderToken + "\",\"appid\":"
                    + properties.appId() + "}";
            String encoded = Base64.getEncoder().encodeToString(
                    tokenJson.getBytes(StandardCharsets.UTF_8));
            LOG.info("Đã tạo đơn ZaloPay {} (openinapp)", request.txnRef());
            return "https://qcgateway.zalopay.vn/openinapp?order=" + encoded;
        }
        Object qrCode = response.get("qr_code");
        String qr = qrCode == null ? null : qrCode.toString();
        if (qr == null || qr.isBlank()) {
            throw new IllegalStateException("ZaloPay create không trả order_url/order_token/qr_code");
        }
        LOG.info("Đã tạo đơn ZaloPay {} -> QR", request.txnRef());
        return qr;
    }

    /**
     * {@code /v2/query} — job đối soát dùng chung. {@code return_code}: 1 đã thanh toán, 2 thất bại
     * ({@code sub_return_code = -101} là không có đơn), 3 đang xử lý / chưa thanh toán.
     */
    @Override
    public TransactionQueryResult queryTransaction(TransactionQuery query) {
        requireConfig();
        return toQueryResult(post(properties.queryUrl(), queryBody(query.txnRef())));
    }

    /** Body {@code /v2/query}; mac = HMAC(key1, {@code app_id|app_trans_id|key1}). */
    Map<String, Object> queryBody(String appTransId) {
        String mac = hmac(properties.appId() + "|" + appTransId + "|" + properties.key1(),
                properties.key1());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app_id", Long.parseLong(properties.appId()));
        body.put("app_trans_id", appTransId);
        body.put("mac", mac);
        return body;
    }

    /** Chuẩn hoá response {@code /v2/query}; số tiền chỉ lấy khi đã thanh toán (cổng chỉ trả lúc đó). */
    TransactionQueryResult toQueryResult(Map<String, Object> response) {
        String raw = toJson(response);
        String message = String.valueOf(response.getOrDefault("return_message", ""));
        int returnCode = intValue(response.get("return_code"), 0);
        int subReturnCode = intValue(response.get("sub_return_code"), 0);
        String transactionNo = response.get("zp_trans_id") == null
                ? null : String.valueOf(response.get("zp_trans_id"));
        return switch (returnCode) {
            case 1 -> new TransactionQueryResult(TransactionQueryResult.Outcome.SUCCESS, transactionNo,
                    epochMillis(response.get("server_time")),
                    response.get("amount") == null ? null : new BigDecimal(String.valueOf(response.get("amount"))),
                    message, raw);
            case 2 -> new TransactionQueryResult(subReturnCode == ORDER_NOT_EXIST
                    ? TransactionQueryResult.Outcome.NOT_FOUND : TransactionQueryResult.Outcome.FAILED,
                    transactionNo, null, null, message, raw);
            case 3 -> new TransactionQueryResult(TransactionQueryResult.Outcome.PENDING, transactionNo,
                    null, null, message, raw);
            default -> new TransactionQueryResult(TransactionQueryResult.Outcome.UNKNOWN, transactionNo,
                    null, null, message, raw);
        };
    }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        String data = params.get("data");
        String mac = params.get("mac");
        return data != null && mac != null
                && constantTimeEquals(hmac(data, properties.key2()), mac);
    }

    @Override
    public GatewayNotification parseNotification(Map<String, String> payload) {
        String data = payload.get("data");
        String mac = payload.get("mac");
        if (data == null || mac == null) {
            throw new IllegalArgumentException("Callback ZaloPay thiếu data/mac");
        }
        if (!constantTimeEquals(hmac(data, properties.key2()), mac)) {
            throw new IllegalArgumentException("Callback ZaloPay sai chữ ký");
        }
        Map<String, Object> json = readJson(data);
        if (!String.valueOf(json.get("app_id")).equals(properties.appId())) {
            throw new IllegalArgumentException("Callback ZaloPay sai app_id");
        }
        Object appTransId = json.get("app_trans_id");
        Object rawAmount = json.get("amount");
        if (appTransId == null || rawAmount == null) {
            throw new IllegalArgumentException("Callback ZaloPay thiếu app_trans_id/amount");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(rawAmount));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Callback ZaloPay amount không hợp lệ", e);
        }
        Instant paidAt = epochMillis(json.get("server_time"));
        String gatewayTransactionNo = json.get("zp_trans_id") == null
                ? null : String.valueOf(json.get("zp_trans_id"));
        // Callback ZaloPay không có field trạng thái: cổng chỉ gọi callback khi đã thanh toán thành
        // công, nên callback hợp lệ (đúng chữ ký + app_id) là SUCCESS. Thất bại/đang xử lý chỉ biết
        // qua /v2/query.
        return new GatewayNotification(String.valueOf(appTransId), PaymentStatus.SUCCESS, amount, paidAt,
                gatewayTransactionNo, data);
    }

    private static int intValue(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String embedData() {
        try {
            if (properties.redirectUrl() == null || properties.redirectUrl().isBlank()) {
                return "{}";
            }
            return objectMapper.writeValueAsString(Map.of("redirecturl", properties.redirectUrl()));
        } catch (Exception e) {
            throw new IllegalStateException("Không serialize được embed_data ZaloPay", e);
        }
    }

    private Map<String, Object> post(String url, Map<String, Object> body) {
        Map<String, Object> response = http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null) {
            throw new IllegalStateException("ZaloPay trả body rỗng");
        }
        return response;
    }

    private Map<String, Object> readJson(String data) {
        try {
            return objectMapper.readValue(data, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được data ZaloPay", e);
        }
    }

    private static Instant epochMillis(Object raw) {
        if (raw == null) {
            return null;
        }
        long millis = raw instanceof Number number
                ? number.longValue() : Long.parseLong(String.valueOf(raw));
        return Instant.ofEpochMilli(millis);
    }

    private void requireConfig() {
        if (blank(properties.appId()) || blank(properties.key1()) || blank(properties.key2())
                || blank(properties.createUrl()) || blank(properties.queryUrl())) {
            throw new IllegalStateException("Thiếu cấu hình ZaloPay (app-id/key1/key2/url)");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
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
