package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.booking.application.DepositPaymentService;
import vn.lemar.selfstorage.booking.controller.InvoicePaymentController;

/** VNPay bắt buộc `vnp_IpAddr`, nên lấy IP khách cho đúng kể cả khi đứng sau proxy. */
class InvoicePaymentControllerTest {

    private final DepositPaymentService service = mock(DepositPaymentService.class);
    private final InvoicePaymentController controller = new InvoicePaymentController(service);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void usesTheFirstHopOfXForwardedForWhenBehindAProxy() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.1");
        when(service.startDeposit(anyLong(), eq("203.0.113.9"))).thenReturn("https://pay");

        assertThat(controller.pay(1L, request).getBody().data().paymentUrl()).isEqualTo("https://pay");
    }

    @Test
    void fallsBackToRemoteAddrWithoutTheHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("198.51.100.4");

        controller.pay(1L, request);

        verify(service).startDeposit(1L, "198.51.100.4");
    }

    @Test
    void blankHeaderIsTreatedAsAbsent() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("198.51.100.4");

        controller.pay(1L, request);

        verify(service).startDeposit(1L, "198.51.100.4");
    }
}
