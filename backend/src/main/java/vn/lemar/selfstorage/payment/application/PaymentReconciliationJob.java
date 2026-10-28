package vn.lemar.selfstorage.payment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chạy đối soát định kỳ; job mỏng, toàn bộ quyết định nằm ở service. */
@Component
public class PaymentReconciliationJob {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentReconciliationService service;

    public PaymentReconciliationJob(PaymentReconciliationService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${payment.reconciliation.fixed-delay-ms:300000}",
            initialDelayString = "${payment.reconciliation.initial-delay-ms:60000}")
    public void reconcilePendingPayments() {
        int applied = service.reconcilePending();
        if (applied > 0) {
            LOG.info("Đối soát đã chốt {} giao dịch Pending", applied);
        }
    }
}
