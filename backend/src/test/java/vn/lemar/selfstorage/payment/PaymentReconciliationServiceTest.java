package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentReconciliationService;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.dto.QueryDrResult;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;

class PaymentReconciliationServiceTest {

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentService payments = mock(PaymentService.class);
    private PaymentReconciliationService service;
    private PaymentTransaction transaction;

    @BeforeEach
    void setUp() {
        service = new PaymentReconciliationService(repository, gateway, payments);
        transaction = mock(PaymentTransaction.class);
        when(transaction.getId()).thenReturn(10L);
        when(transaction.getInvoiceId()).thenReturn(7L);
        when(transaction.getVnpTxnRef()).thenReturn("TXN1");
        when(transaction.getTransactionContent()).thenReturn("Dat coc");
        when(transaction.getAmount()).thenReturn(new BigDecimal("500000"));
        when(transaction.getCreatedAt()).thenReturn(Instant.now().minusSeconds(16 * 60));
        when(repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                any(), any(), any(Pageable.class))).thenReturn(List.of(transaction));
    }

    @Test
    void gatewaySuccessMarksThePendingTransaction() {
        QueryDrResult result = new QueryDrResult("00", "00", "GW1", Instant.now(),
                new BigDecimal("500000"), "ok", "{}");
        when(gateway.queryDr(any())).thenReturn(result);
        when(payments.reconcileSuccess(eq(10L), eq("GW1"), any(), eq("{}"))).thenReturn(true);

        assertThat(service.reconcilePending()).isEqualTo(1);
        verify(payments).reconcileSuccess(eq(10L), eq("GW1"), any(), eq("{}"));
    }

    @Test
    void gatewayFailedStatusMarksTheTransactionFailed() {
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult("00", "02", "GW1", null,
                new BigDecimal("500000"), "failed", "{}"));
        when(payments.reconcileFailure(10L, "querydr: failed", "{}")).thenReturn(true);

        assertThat(service.reconcilePending()).isEqualTo(1);
        verify(payments).reconcileFailure(10L, "querydr: failed", "{}");
    }

    @Test
    void notFoundClosesThePendingTransaction() {
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult("91", null, null, null,
                null, "not found", "{}"));
        when(payments.reconcileFailure(10L, "querydr: không tìm thấy giao dịch", "{}")).thenReturn(true);

        assertThat(service.reconcilePending()).isEqualTo(1);
        verify(payments).reconcileFailure(10L, "querydr: không tìm thấy giao dịch", "{}");
    }

    @Test
    void stillPendingAndYoungStaysPending() {
        when(transaction.getCreatedAt()).thenReturn(Instant.now().minusSeconds(16 * 60));
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult("00", "01", "GW1", null,
                new BigDecimal("500000"), "pending", "{}"));

        assertThat(service.reconcilePending()).isZero();
        verifyNoInteractions(payments);
    }

    @Test
    void stillPendingPastMaxAgeIsClosed() {
        when(transaction.getCreatedAt()).thenReturn(Instant.now().minusSeconds(61 * 60));
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult("00", "01", "GW1", null,
                new BigDecimal("500000"), "pending", "{}"));
        when(payments.reconcileFailure(10L, "querydr: chưa hoàn tất quá 60 phút", "{}"))
                .thenReturn(true);

        assertThat(service.reconcilePending()).isEqualTo(1);
        verify(payments).reconcileFailure(10L, "querydr: chưa hoàn tất quá 60 phút", "{}");
    }

    @Test
    void amountMismatchIsNotApplied() {
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult("00", "00", "GW1", Instant.now(),
                new BigDecimal("400000"), "ok", "{}"));

        assertThat(service.reconcilePending()).isZero();
        verifyNoInteractions(payments);
    }

    @Test
    void gatewayErrorKeepsTheTransactionPending() {
        when(gateway.queryDr(any())).thenThrow(new IllegalStateException("checksum"));
        when(payments.reconcileFailure(anyLong(), any(), any())).thenReturn(false);

        assertThat(service.reconcilePending()).isZero();
        verify(payments, never()).reconcileFailure(anyLong(), any(), any());
    }
}
