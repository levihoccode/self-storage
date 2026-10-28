package vn.lemar.selfstorage.payment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.PaymentReconciliationJob;
import vn.lemar.selfstorage.payment.application.PaymentReconciliationService;

class PaymentReconciliationJobTest {

    private final PaymentReconciliationService service = mock(PaymentReconciliationService.class);
    private final PaymentReconciliationJob job = new PaymentReconciliationJob(service);

    @Test
    void jobDelegatesToTheService() {
        when(service.reconcilePending()).thenReturn(3);

        job.reconcilePendingPayments();

        verify(service).reconcilePending();
    }
}
