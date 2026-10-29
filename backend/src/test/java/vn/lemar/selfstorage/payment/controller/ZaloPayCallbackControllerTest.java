package vn.lemar.selfstorage.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentNotificationResult;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;

class ZaloPayCallbackControllerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final ZaloPayCallbackController controller =
            new ZaloPayCallbackController(paymentService, gateway);

    @Test
    void acceptedCallbackIsAcknowledged() {
        when(gateway.parseNotification(any())).thenReturn(notification());
        when(paymentService.handleNotification(any()))
                .thenReturn(PaymentNotificationResult.ACCEPTED);

        assertThat(controller.callback(Map.<String, Object>of("data", "{}", "mac", "m")))
                .containsEntry("return_code", 1);
    }

    @Test
    void invalidSignatureIsNotAcknowledged() {
        when(gateway.parseNotification(any()))
                .thenThrow(new IllegalArgumentException("sai chữ ký"));

        assertThat(controller.callback(Map.<String, Object>of("data", "{}", "mac", "bad")))
                .containsEntry("return_code", 0);
    }

    @Test
    void unknownTransactionIsNotAcknowledged() {
        when(gateway.parseNotification(any())).thenReturn(notification());
        when(paymentService.handleNotification(any()))
                .thenReturn(PaymentNotificationResult.NOT_FOUND);

        assertThat(controller.callback(Map.<String, Object>of("data", "{}", "mac", "m")))
                .containsEntry("return_code", 0);
    }

    @Test
    void returnPageOnlyEchoesTheStatus() {
        assertThat(controller.returnPage(Map.of("status", "1", "apptransid", "TXN")))
                .containsEntry("provider", "zalopay")
                .containsEntry("status", "1")
                .containsEntry("appTransId", "TXN");
    }

    private static GatewayNotification notification() {
        return new GatewayNotification("TXN", PaymentStatus.SUCCESS, new BigDecimal("10000"),
                Instant.now(), "ZP", "{}");
    }
}
