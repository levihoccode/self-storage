package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.booking.application.DepositPaymentListener;
import vn.lemar.selfstorage.booking.application.DepositStateRepairJob;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.payment.application.PaymentSessionQuery;
import vn.lemar.selfstorage.payment.application.dto.SuccessfulPayment;
import vn.lemar.selfstorage.payment.application.event.PaymentSucceeded;

class DepositStateRepairJobTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentSessionQuery payments = mock(PaymentSessionQuery.class);
    private final DepositPaymentListener listener = mock(DepositPaymentListener.class);
    private final DepositStateRepairJob job = new DepositStateRepairJob(invoices, payments, listener);

    @Test
    void successfulPaymentReplaysTheListenerForAnUnpaidInvoice() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(9L);
        when(invoices.findByStatusAndTypeIn(any(), any())).thenReturn(List.of(invoice));
        when(payments.findSuccessfulPayment(9L)).thenReturn(Optional.of(
                new SuccessfulPayment(9L, "TXN9", new BigDecimal("10000"), Instant.now())));

        job.repairUnpaidDeposits();

        verify(listener).onPaymentSucceeded(any(PaymentSucceeded.class));
    }

    @Test
    void listenerFailureDoesNotBreakTheJob() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(9L);
        when(invoices.findByStatusAndTypeIn(any(), any())).thenReturn(List.of(invoice));
        when(payments.findSuccessfulPayment(9L)).thenReturn(Optional.of(
                new SuccessfulPayment(9L, "TXN9", new BigDecimal("10000"), Instant.now())));
        org.mockito.Mockito.doThrow(new IllegalStateException("notify failed"))
                .when(listener).onPaymentSucceeded(any());

        assertThatCode(job::repairUnpaidDeposits).doesNotThrowAnyException();
    }
}
