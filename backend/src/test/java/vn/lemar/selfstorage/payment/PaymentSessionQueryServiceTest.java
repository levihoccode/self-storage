package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.PaymentSessionQueryService;
import vn.lemar.selfstorage.payment.domain.PaymentDirection;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;

/** Khóa giao dịch ngắn hạn suy ra từ giao dịch còn Pending — không có khóa riêng để lệch. */
class PaymentSessionQueryServiceTest {

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
    private final PaymentSessionQueryService service = new PaymentSessionQueryService(repository);

    @Test
    void noInvoicesMeansNoOpenSessionAndNoQuery() {
        assertThat(service.hasOpenSession(List.of())).isFalse();
        assertThat(service.hasOpenSession(null)).isFalse();

        verifyNoInteractions(repository);
    }

    @Test
    void anOpenSessionIsAPendingTransactionInsideTheWindow() {
        when(repository.existsByInvoiceIdInAndStatusAndCreatedAtAfter(
                anyCollection(), any(PaymentStatus.class), any(Instant.class))).thenReturn(true);

        assertThat(service.hasOpenSession(List.of(1L, 2L))).isTrue();
    }

    @Test
    void successfulPaymentIsReportedForTheInvoice() {
        when(repository.existsByInvoiceIdAndDirectionAndStatus(
                1L, PaymentDirection.PAY, PaymentStatus.SUCCESS)).thenReturn(true);

        assertThat(service.hasSuccessfulPayment(1L)).isTrue();
    }

    @Test
    void noPendingTransactionMeansTheUnitIsFree() {
        when(repository.existsByInvoiceIdInAndStatusAndCreatedAtAfter(
                anyCollection(), any(PaymentStatus.class), any(Instant.class))).thenReturn(false);

        assertThat(service.hasOpenSession(List.of(1L))).isFalse();
    }
}
