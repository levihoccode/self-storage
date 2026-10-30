package vn.lemar.selfstorage.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.ResponseEntity;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentNotificationResult;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

class PayOsWebhookControllerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PayOsWebhookController controller =
            new PayOsWebhookController(paymentService, gateway, new ObjectMapper());

    /** Webhook đúng chữ ký luôn 200 — kể cả giao dịch mẫu của confirm-webhook (không có trong DB). */
    @ParameterizedTest
    @EnumSource(PaymentNotificationResult.class)
    void everyResultOfAValidWebhookIsAcknowledged(PaymentNotificationResult result) {
        when(gateway.parseNotification(any())).thenReturn(notification());
        when(paymentService.handleNotification(any())).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = controller.webhook(body());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true).containsEntry("result", result.name());
    }

    @Test
    void dataObjectIsHandedToTheAdapterAsJsonWithItsSignature() {
        when(gateway.parseNotification(any())).thenReturn(notification());
        when(paymentService.handleNotification(any())).thenReturn(PaymentNotificationResult.ACCEPTED);

        controller.webhook(body());

        verify(gateway).parseNotification(argThat(payload ->
                "{\"orderCode\":123,\"amount\":3000,\"code\":\"00\"}".equals(payload.get("data"))
                        && "sig".equals(payload.get("signature"))));
    }

    @Test
    void invalidWebhookIsRejectedWith400() {
        when(gateway.parseNotification(any())).thenThrow(new IllegalArgumentException("sai chữ ký"));

        ResponseEntity<Map<String, Object>> response = controller.webhook(body());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void webhookWithoutDataObjectReachesTheAdapterWithoutData() {
        when(gateway.parseNotification(argThat(payload -> payload.get("data") == null)))
                .thenThrow(new IllegalArgumentException("thiếu data"));

        assertThat(controller.webhook(Map.of("signature", "sig")).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void systemErrorWhileRecordingReturns500() {
        when(gateway.parseNotification(any())).thenReturn(notification());
        when(paymentService.handleNotification(any())).thenThrow(new IllegalStateException("db down"));

        assertThat(controller.webhook(body()).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void returnPageEchoesTheQueryWithoutWriting() {
        assertThat(controller.returnPage(Map.of("status", "PAID", "cancel", "false", "orderCode", "123")))
                .containsEntry("provider", "payos")
                .containsEntry("status", "PAID")
                .containsEntry("cancel", "false")
                .containsEntry("orderCode", "123");
        assertThat(controller.returnPage(Map.of())).containsEntry("status", "");
    }

    private static Map<String, Object> body() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 123);
        data.put("amount", 3000);
        data.put("code", "00");
        return Map.of("code", "00", "desc", "success", "success", true, "data", data, "signature", "sig");
    }

    private static GatewayNotification notification() {
        return new GatewayNotification("123", PaymentStatus.SUCCESS, new BigDecimal("3000"),
                Instant.parse("2026-10-30T03:00:00Z"), "FT001", "{}");
    }
}
