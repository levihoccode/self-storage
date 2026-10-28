package vn.lemar.selfstorage.payment.application;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.application.dto.QueryDrRequest;
import vn.lemar.selfstorage.payment.application.dto.QueryDrResult;
import vn.lemar.selfstorage.payment.config.VnPayProperties;

/**
 * Cài đặt {@link PaymentGateway} cho VNPay.
 *
 * <p>Ba quy định của VNPay được xử lý ở đây, sai một cái là cổng từ chối:
 * số tiền phải nhân 100, mốc thời gian theo múi giờ GMT+7 dạng {@code yyyyMMddHHmmss},
 * và chữ ký HMAC-SHA512 tính trên chuỗi tham số đã sắp xếp theo thứ tự alphabet.
 */
@Component
public class VnPayGateway implements PaymentGateway {

    private static final String VERSION = "2.1.0";
    private static final String COMMAND = "pay";
    private static final String CURRENCY = "VND";
    private static final String LOCALE = "vn";
    private static final String ORDER_TYPE = "other";
    private static final String HASH_FIELD = "vnp_SecureHash";
    private static final String HASH_TYPE_FIELD = "vnp_SecureHashType";

    /** VNPay nhận số tiền không có phần thập phân nên quy ước nhân 100 trước khi gửi. */
    private static final BigDecimal AMOUNT_SCALE = BigDecimal.valueOf(100);

    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Duration PAYMENT_WINDOW = PaymentSession.WINDOW;

    private static final String QUERY_DR_COMMAND = "querydr";
    private static final String SUCCESS_CODE = "00";
    private static final Logger LOG = LoggerFactory.getLogger(VnPayGateway.class);

    private final VnPayProperties properties;
    private final RestClient http = RestClient.create();

    public VnPayGateway(VnPayProperties properties) {
        this.properties = properties;
    }

    @Override
    public String buildPaymentUrl(PaymentRequest request) {
        return buildPaymentUrl(request, Instant.now());
    }

    @Override
    public String buildPaymentUrl(PaymentRequest request, Instant createdAt) {
        LocalDateTime now = LocalDateTime.ofInstant(createdAt, VNPAY_ZONE);

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", COMMAND);
        params.put("vnp_TmnCode", properties.tmnCode());
        params.put("vnp_Amount", request.amount().multiply(AMOUNT_SCALE).toBigInteger().toString());
        params.put("vnp_CurrCode", CURRENCY);
        params.put("vnp_TxnRef", request.txnRef());
        params.put("vnp_OrderInfo", asciiFold(request.orderInfo()));
        params.put("vnp_OrderType", ORDER_TYPE);
        params.put("vnp_Locale", LOCALE);
        params.put("vnp_ReturnUrl", properties.returnUrl());
        params.put("vnp_IpAddr", request.ipAddr());
        params.put("vnp_CreateDate", now.format(TIMESTAMP));
        params.put("vnp_ExpireDate", now.plus(PAYMENT_WINDOW).format(TIMESTAMP));

        String query = toSignedQuery(params);
        return properties.payUrl() + "?" + query + "&" + HASH_FIELD + "=" + sign(query);
    }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        String received = params.get(HASH_FIELD);
        if (received == null || received.isBlank()) {
            return false;
        }
        Map<String, String> signed = new TreeMap<>(params);
        signed.remove(HASH_FIELD);
        signed.remove(HASH_TYPE_FIELD);

        return constantTimeEquals(sign(toSignedQuery(signed)), received);
    }

    @Override
    public QueryDrResult queryDr(QueryDrRequest request) {
        LocalDateTime now = LocalDateTime.now(VNPAY_ZONE);
        Map<String, String> params = queryDrParams(request, now,
                UUID.randomUUID().toString().replace("-", ""));

        Map<String, String> response = http.post()
                .uri(properties.queryDrUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(params)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, String>>() {});
        if (response == null) {
            throw new IllegalStateException("VNPay querydr trả body rỗng");
        }
        return parseQueryDrResponse(response);
    }

    /** Dựng body querydr — tách khỏi HTTP để test chữ ký và thứ tự field. */
    Map<String, String> queryDrParams(QueryDrRequest request, LocalDateTime now, String requestId) {
        String orderInfo = request.orderInfo() == null ? "" : asciiFold(request.orderInfo());
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_RequestId", requestId);
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", QUERY_DR_COMMAND);
        params.put("vnp_TmnCode", properties.tmnCode());
        params.put("vnp_TxnRef", request.txnRef());
        params.put("vnp_TransactionDate", format(request.transactionDate()));
        params.put("vnp_CreateDate", now.format(TIMESTAMP));
        params.put("vnp_IpAddr", request.ipAddr());
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_SecureHash", sign(queryDrRequestData(params)));
        return params;
    }

    /** Xác thực chữ ký response và map sang kết quả nghiệp vụ. */
    QueryDrResult parseQueryDrResponse(Map<String, String> response) {
        String responseCode = response.get("vnp_ResponseCode");
        String received = response.get(HASH_FIELD);
        if (received == null || received.isBlank()) {
            // VNPay không ký response lỗi (ví dụ 99 Unexpected error). Không có trạng thái giao
            // dịch để áp dụng, nên trả về lỗi API để service giữ Pending và retry.
            if (!SUCCESS_CODE.equals(responseCode)) {
                return new QueryDrResult(responseCode, response.get("vnp_TransactionStatus"),
                        response.get("vnp_TransactionNo"), null, null,
                        response.get("vnp_Message"), response.toString());
            }
            throw new IllegalStateException("VNPay querydr success thiếu checksum");
        }
        if (!constantTimeEquals(sign(queryDrResponseData(response)), received)) {
            LOG.warn("VNPay querydr checksum mismatch: data={} expected={} received={}",
                    queryDrResponseData(response), sign(queryDrResponseData(response)), received);
            throw new IllegalStateException("VNPay querydr checksum không hợp lệ");
        }
        return new QueryDrResult(
                responseCode,
                response.get("vnp_TransactionStatus"),
                response.get("vnp_TransactionNo"),
                parsePayDate(response.get("vnp_PayDate")),
                parseAmount(response.get("vnp_Amount")),
                response.get("vnp_Message"),
                response.toString());
    }

    /**
     * Thứ tự field theo spec querydr:
     * RequestId|Version|Command|TmnCode|TxnRef|TransactionDate|CreateDate|IpAddr|OrderInfo.
     */
    private String queryDrRequestData(Map<String, String> params) {
        return String.join("|",
                blank(params.get("vnp_RequestId")),
                blank(params.get("vnp_Version")),
                blank(params.get("vnp_Command")),
                blank(params.get("vnp_TmnCode")),
                blank(params.get("vnp_TxnRef")),
                blank(params.get("vnp_TransactionDate")),
                blank(params.get("vnp_CreateDate")),
                blank(params.get("vnp_IpAddr")),
                blank(params.get("vnp_OrderInfo")));
    }

    /** Thứ tự field response theo spec querydr. */
    private String queryDrResponseData(Map<String, String> response) {
        return String.join("|",
                blank(response.get("vnp_ResponseId")),
                blank(response.get("vnp_Command")),
                blank(response.get("vnp_ResponseCode")),
                blank(response.get("vnp_Message")),
                blank(response.get("vnp_TmnCode")),
                blank(response.get("vnp_TxnRef")),
                blank(response.get("vnp_Amount")),
                blank(response.get("vnp_BankCode")),
                blank(response.get("vnp_PayDate")),
                blank(response.get("vnp_TransactionNo")),
                blank(response.get("vnp_TransactionType")),
                blank(response.get("vnp_TransactionStatus")),
                blank(response.get("vnp_OrderInfo")),
                blank(response.get("vnp_PromotionCode")),
                blank(response.get("vnp_PromotionAmount")));
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String format(Instant instant) {
        return instant == null ? "" : LocalDateTime.ofInstant(instant, VNPAY_ZONE).format(TIMESTAMP);
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw).divide(AMOUNT_SCALE);
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static Instant parsePayDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, TIMESTAMP).atZone(VNPAY_ZONE).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * VNPay chỉ hiển thị được ASCII: chuỗi có dấu bị encode US-ASCII thành {@code ?} và khách thấy
     * "??t c?c khoang ch?a A-01". Bỏ dấu trước khi ký để chuỗi ký và chuỗi hiển thị khớp nhau.
     */
    private static String asciiFold(String value) {
        if (value == null) {
            return null;
        }
        String folded = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return folded.replace('đ', 'd').replace('Đ', 'D');
    }

    /** Ghép tham số đã sắp xếp thành chuỗi url-encoded — đây chính là dữ liệu đem ký. */
    private String toSignedQuery(Map<String, String> params) {
        StringBuilder data = new StringBuilder();
        new TreeMap<>(params).forEach((key, value) -> {
            if (value == null || value.isEmpty()) {
                return;
            }
            if (data.length() > 0) {
                data.append('&');
            }
            data.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        });
        return data.toString();
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    properties.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Không ký được dữ liệu VNPay", e);
        }
    }

    /** So sánh không phụ thuộc vị trí byte lệch, tránh lộ thông tin qua thời gian phản hồi. */
    private boolean constantTimeEquals(String expected, String received) {
        return java.security.MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}
