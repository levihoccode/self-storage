package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.booking.application.DepositPaymentListener;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.domain.StorageUnit;
import vn.lemar.selfstorage.booking.domain.StorageUnitStatus;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.booking.repository.PolicyReader;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.booking.repository.StorageUnitRepository;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationType;
import vn.lemar.selfstorage.payment.application.event.PaymentFailed;
import vn.lemar.selfstorage.payment.application.event.PaymentSucceeded;

/** Nhánh "Thành công" của spec Flow 1.4, và hai annotation giữ cho nó chạy đúng chỗ. */
class DepositPaymentListenerTest {

    private static final Long INVOICE_ID = 100L;
    private static final Long ORDER_ID = 200L;
    private static final Long UNIT_ID = 300L;
    private static final Long CUSTOMER_ID = 7L;

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final RentalOrderRepository orders = mock(RentalOrderRepository.class);
    private final StorageUnitRepository units = mock(StorageUnitRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final PolicyReader policies = mock(PolicyReader.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);

    private DepositPaymentListener listener;
    private Invoice invoice;
    private RentalOrder order;
    private StorageUnit unit;

    @BeforeEach
    void setUp() {
        listener = new DepositPaymentListener(invoices, orders, units, notifications, policies, audit);

        invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(INVOICE_ID);
        when(invoice.getOrderId()).thenReturn(ORDER_ID);
        when(invoice.getCustomerId()).thenReturn(CUSTOMER_ID);
        when(invoice.isUnpaid()).thenReturn(true);

        order = mock(RentalOrder.class);
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getUnitId()).thenReturn(UNIT_ID);

        unit = mock(StorageUnit.class);
        when(unit.getId()).thenReturn(UNIT_ID);
        when(unit.getCode()).thenReturn("A-01");
        when(unit.getStatus()).thenReturn(StorageUnitStatus.AVAILABLE);

        when(invoices.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(units.findById(UNIT_ID)).thenReturn(Optional.of(unit));
        when(policies.intValue(eq("order.deposit_expiry_days"), any(Integer.class).intValue()))
                .thenReturn(30);
    }

    private PaymentSucceeded succeeded() {
        return new PaymentSucceeded(INVOICE_ID, "INV100T20261013120000",
                new BigDecimal("10000.00"), Instant.now());
    }

    @Test
    void successUpdatesInvoiceOrderAndUnitTogether() {
        listener.onPaymentSucceeded(succeeded());

        verify(invoice).markPaid();
        verify(order).markDeposited(any(Instant.class));
        verify(unit).reserve();
    }

    @Test
    void successWritesOneAuditEntryPerChangedEntity() {
        listener.onPaymentSucceeded(succeeded());

        // RentalOrder + StorageUnit; PaymentTransaction do module payment tự ghi.
        verify(audit, times(2)).record(any());
    }

    @Test
    void successNotifiesTheCustomer() {
        listener.onPaymentSucceeded(succeeded());

        verify(notifications).notify(eq(CUSTOMER_ID), any(), any(),
                eq(NotificationType.DEPOSIT_PAYMENT_SUCCEEDED), any(), eq(ORDER_ID));
    }

    @Test
    void holdDeadlineComesFromPolicy() {
        when(policies.intValue(eq("order.deposit_expiry_days"), any(Integer.class).intValue()))
                .thenReturn(1);
        Instant before = Instant.now();

        listener.onPaymentSucceeded(succeeded());

        org.mockito.ArgumentCaptor<Instant> captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(order).markDeposited(captor.capture());
        assertThat(captor.getValue()).isAfter(before).isBefore(before.plusSeconds(2 * 24 * 3600));
    }

    @Test
    void alreadyPaidInvoiceIsLeftAlone() {
        when(invoice.isUnpaid()).thenReturn(false);

        listener.onPaymentSucceeded(succeeded());

        verify(invoice, never()).markPaid();
        verifyNoInteractions(audit);
        verifyNoInteractions(notifications);
    }

    @Test
    void missingInvoiceDoesNotThrowSoTheIpnStaysConfirmed() {
        when(invoices.findById(INVOICE_ID)).thenReturn(Optional.empty());

        listener.onPaymentSucceeded(succeeded());

        verifyNoInteractions(audit);
    }

    @Test
    void failureChangesNothingBecauseTheLockExpiresWithTheTransaction() {
        listener.onPaymentFailed(new PaymentFailed(INVOICE_ID, "INV100T1", new BigDecimal("10000.00"),
                "vnp_ResponseCode=24"));

        verifyNoInteractions(invoices);
        verifyNoInteractions(units);
        verifyNoInteractions(audit);
    }

    /**
     * Hai annotation này là điều kiện sống còn, không phải trang trí: thiếu AFTER_COMMIT thì
     * listener đọc state chưa commit và exception của nó rollback luôn giao dịch IPN; thiếu
     * REQUIRES_NEW thì AuditRecorder (propagation MANDATORY) ném lỗi vì không có transaction.
     */
    @Test
    void successListenerRunsAfterCommitInItsOwnTransaction() throws Exception {
        Method method = DepositPaymentListener.class
                .getMethod("onPaymentSucceeded", PaymentSucceeded.class);

        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(method.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
