package vn.lemar.selfstorage.payment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.PaymentReconciliationJob;
import vn.lemar.selfstorage.payment.application.PaymentReconciliationService;
import vn.lemar.selfstorage.payment.application.PaymentService;

class PaymentReconciliationJobTest {

    private final PaymentReconciliationService service = mock(PaymentReconciliationService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final PaymentReconciliationJob job = new PaymentReconciliationJob(service, payments);

    @Test
    void jobDelegatesToTheService() {
        when(service.reconcilePending()).thenReturn(3);

        job.reconcilePendingPayments();

        verify(service).reconcilePending();
    }

    @Test
    void jobClearsPaymentUrlsOfExpiredSessions() {
        when(payments.forgetExpiredPaymentUrls()).thenReturn(2);

        job.reconcilePendingPayments();

        verify(payments).forgetExpiredPaymentUrls();
    }
}
