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
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult;
import vn.lemar.selfstorage.payment.application.dto.TransactionQueryResult.Outcome;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;

/** Đối soát dùng chung cho mọi cổng: chỉ đọc kết quả đã chuẩn hoá, không đọc mã riêng của cổng. */
class PaymentReconciliationServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("500000");

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
        when(transaction.getAmount()).thenReturn(AMOUNT);
        when(transaction.getCreatedAt()).thenReturn(Instant.now().minusSeconds(16 * 60));
        when(gateway.provider()).thenReturn("test");
        when(repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                any(), any(), any(Pageable.class))).thenReturn(List.of(transaction));
    }

    @Test
    void gatewaySuccessMarksThePendingTransaction() {
        when(gateway.queryTransaction(any())).thenReturn(result(Outcome.SUCCESS, AMOUNT, "ok"));
        when(payments.reconcileSuccess(eq(10L), eq("GW1"), any(), eq("{}"))).thenReturn(true);

        assertThat(service.reconcilePending()).isEqualTo(1);
        verify(payments).reconcileSuccess(eq(10L), eq("GW1"), any(), eq("{}"));
    }

    @Test
    void gatewayFailedStatusMarksTheTransactionFailed() {
        when(gateway.queryTransaction(any())).thenReturn(result(Outcome.FAILED, AMOUNT, "failed"));
        when(payments.reconcileFailure(10L, "đối soát: failed", "{}")).thenReturn(true);

        assertThat(service.reconcilePending()).isEqualTo(1);
        verify(payments).reconcileFailure(10L, "đối soát: failed", "{}");
    }

    @Test
    void failedWithoutAmountIsStillClosed() {
        // ZaloPay chỉ trả amount khi đã thanh toán — thất bại không kèm số tiền vẫn phải đóng được.
        when(gateway.queryTransaction(any())).thenReturn(result(Outcome.FAILED, null, "failed"));
        when(payments.reconcileFailure(10L, "đối soát: failed", "{}")).thenReturn(true);

        assertThat(service.reconcilePending()).isEqualTo(1);
    }

    @Test
    void notFoundClosesThePendingTransaction() {
        when(gateway.queryTransaction(any())).thenReturn(result(Outcome.NOT_FOUND, null, "not found"));
        when(payments.reconcileFailure(10L, "đối soát: không tìm thấy giao dịch", "{}")).thenReturn(true);

        assertThat(service.reconcilePending()).isEqualTo(1);
        verify(payments).reconcileFailure(10L, "đối soát: không tìm thấy giao dịch", "{}");
    }

    @Test
    void unknownKeepsTheTransactionPending() {
        when(gateway.queryTransaction(any())).thenReturn(result(Outcome.UNKNOWN, null, "api error"));

        assertThat(service.reconcilePending()).isZero();
        verifyNoInteractions(payments);
    }

    @Test
    void stillPendingAndYoungStaysPending() {
        when(gateway.queryTransaction(any())).thenReturn(result(Outcome.PENDING, null, "pending"));

        assertThat(service.reconcilePending()).isZero();
        verifyNoInteractions(payments);
    }

    @Test
    void stillPendingPastMaxAgeIsClosed() {
        when(transaction.getCreatedAt()).thenReturn(Instant.now().minusSeconds(61 * 60));
        when(gateway.queryTransaction(any())).thenReturn(result(Outcome.PENDING, null, "pending"));
        when(payments.reconcileFailure(10L, "đối soát: chưa hoàn tất quá 60 phút", "{}")).thenReturn(true);

        assertThat(service.reconcilePending()).isEqualTo(1);
        verify(payments).reconcileFailure(10L, "đối soát: chưa hoàn tất quá 60 phút", "{}");
    }

    @Test
    void amountMismatchIsNotApplied() {
        when(gateway.queryTransaction(any())).thenReturn(result(Outcome.SUCCESS, new BigDecimal("400000"), "ok"));

        assertThat(service.reconcilePending()).isZero();
        verifyNoInteractions(payments);
    }

    @Test
    void successWithoutAmountIsNotApplied() {
        when(gateway.queryTransaction(any())).thenReturn(result(Outcome.SUCCESS, null, "ok"));

        assertThat(service.reconcilePending()).isZero();
        verifyNoInteractions(payments);
    }

    @Test
    void gatewayErrorKeepsTheTransactionPending() {
        when(gateway.queryTransaction(any())).thenThrow(new IllegalStateException("checksum"));

        assertThat(service.reconcilePending()).isZero();
        verify(payments, never()).reconcileFailure(anyLong(), any(), any());
    }

    private static TransactionQueryResult result(Outcome outcome, BigDecimal amount, String message) {
        return new TransactionQueryResult(outcome, "GW1", Instant.now(), amount, message, "{}");
    }
}
