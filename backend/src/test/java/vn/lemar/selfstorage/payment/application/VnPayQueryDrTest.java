package vn.lemar.selfstorage.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.dto.QueryDrRequest;
import vn.lemar.selfstorage.payment.application.dto.QueryDrResult;
import vn.lemar.selfstorage.payment.config.VnPayProperties;

class VnPayQueryDrTest {

    private static final String SECRET = "TESTSECRET0123456789TESTSECRET01";

    private final VnPayGateway gateway = new VnPayGateway(new VnPayProperties(
            "TESTTMN1", SECRET, "https://pay", "https://return", "https://query"));

    @Test
    void requestUsesTheOriginalTransactionDateAndTheSpecFieldOrder() throws Exception {
        QueryDrRequest request = new QueryDrRequest(
                "TXN1", "Dat coc khoang A-01", Instant.parse("2026-10-28T12:20:00Z"), "127.0.0.1");

        Map<String, String> params = gateway.queryDrParams(
                request, LocalDateTime.of(2026, 10, 28, 20, 30), "REQ1");

        assertThat(params)
                .containsEntry("vnp_RequestId", "REQ1")
                .containsEntry("vnp_Command", "querydr")
                .containsEntry("vnp_TransactionDate", "20261028192000")
                .containsEntry("vnp_CreateDate", "20261028203000")
                .containsEntry("vnp_OrderInfo", "Dat coc khoang A-01");
        assertThat(params.get("vnp_SecureHash")).isEqualTo(hmac(
                "REQ1|2.1.0|querydr|TESTTMN1|TXN1|20261028192000|20261028203000|127.0.0.1"
                        + "|Dat coc khoang A-01"));
    }

    @Test
    void responseIsMappedAfterSignatureVerification() throws Exception {
        QueryDrResult result = gateway.parseQueryDrResponse(response());

        assertThat(result.responseCode()).isEqualTo("00");
        assertThat(result.transactionStatus()).isEqualTo("00");
        assertThat(result.transactionNo()).isEqualTo("15717861");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(result.paidAt()).isEqualTo(Instant.parse("2026-10-28T13:22:41Z"));
    }

    @Test
    void invalidResponseSignatureIsRejected() throws Exception {
        Map<String, String> response = response();
        response.put("vnp_SecureHash", "bad");

        assertThatThrownBy(() -> gateway.parseQueryDrResponse(response))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unsignedApiErrorIsReturnedWithoutApplyingState() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("vnp_ResponseCode", "99");
        response.put("vnp_Message", "Unexpected error");

        QueryDrResult result = gateway.parseQueryDrResponse(response);

        assertThat(result.responseCode()).isEqualTo("99");
        assertThat(result.transactionStatus()).isNull();
        assertThat(result.amount()).isNull();
    }

    private Map<String, String> response() throws Exception {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("vnp_ResponseId", "RESP1");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "Success");
        response.put("vnp_TmnCode", "TESTTMN1");
        response.put("vnp_TxnRef", "TXN1");
        response.put("vnp_Amount", "50000000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261028202241");
        response.put("vnp_TransactionNo", "15717861");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", "00");
        response.put("vnp_OrderInfo", "Dat coc khoang A-01");
        response.put("vnp_PromotionCode", "");
        response.put("vnp_PromotionAmount", "");
        response.put("vnp_SecureHash", hmac(String.join("|",
                response.get("vnp_ResponseId"), response.get("vnp_Command"),
                response.get("vnp_ResponseCode"), response.get("vnp_Message"),
                response.get("vnp_TmnCode"), response.get("vnp_TxnRef"),
                response.get("vnp_Amount"), response.get("vnp_BankCode"),
                response.get("vnp_PayDate"), response.get("vnp_TransactionNo"),
                response.get("vnp_TransactionType"), response.get("vnp_TransactionStatus"),
                response.get("vnp_OrderInfo"), response.get("vnp_PromotionCode"),
                response.get("vnp_PromotionAmount"))));
        return response;
    }

    private static String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
