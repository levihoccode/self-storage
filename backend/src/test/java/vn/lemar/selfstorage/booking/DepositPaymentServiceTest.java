package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.booking.application.DepositPaymentService;
import vn.lemar.selfstorage.booking.application.InvoiceVoidingService;
import vn.lemar.selfstorage.booking.application.exception.DepositNotAllowedException;
import vn.lemar.selfstorage.booking.application.exception.EmailNotVerifiedException;
import vn.lemar.selfstorage.booking.application.exception.InvoiceNotFoundException;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.domain.StorageUnit;
import vn.lemar.selfstorage.booking.domain.StorageUnitStatus;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.booking.repository.StorageUnitRepository;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.PaymentSessionQuery;

/**
 * Năm nhánh kiểm của spec Flow 1.4, cộng hai cửa trước là sở hữu hóa đơn và xác minh email.
 *
 * <p>Thứ tự nhánh cũng là thứ tự được kiểm: mỗi test dựng đúng một điều kiện hỏng và xác nhận
 * service dừng ở đó, không đi tiếp xuống nhánh sau.
 */
class DepositPaymentServiceTest {

    private static final Long CUSTOMER_ID = 7L;
    private static final Long INVOICE_ID = 100L;
    private static final Long ORDER_ID = 200L;
    private static final Long UNIT_ID = 300L;
    private static final Long FACILITY_ID = 400L;
    private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?x=1";

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final RentalOrderRepository orders = mock(RentalOrderRepository.class);
    private final StorageUnitRepository units = mock(StorageUnitRepository.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final PaymentSessionQuery sessions = mock(PaymentSessionQuery.class);
    private final CurrentAccountProvider currentAccount = mock(CurrentAccountProvider.class);
    private final InvoiceVoidingService invoiceVoiding = mock(InvoiceVoidingService.class);

    private DepositPaymentService service;
    private Invoice invoice;
    private RentalOrder order;
    private StorageUnit unit;

    @BeforeEach
    void setUp() {
        service = new DepositPaymentService(invoices, orders, units, payments, sessions,
                currentAccount, invoiceVoiding);

        when(currentAccount.isCurrentAccountVerified()).thenReturn(true);
        when(currentAccount.getCurrentAccountId()).thenReturn(CUSTOMER_ID);

        invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(INVOICE_ID);
        when(invoice.getOrderId()).thenReturn(ORDER_ID);
        when(invoice.getCustomerId()).thenReturn(CUSTOMER_ID);
        when(invoice.belongsTo(CUSTOMER_ID)).thenReturn(true);
        when(invoice.isUnpaid()).thenReturn(true);
        when(invoice.getType()).thenReturn("Deposit");
        when(invoice.getAmount()).thenReturn(new BigDecimal("10000.00"));
        when(invoice.getTitle()).thenReturn("Dat coc khoang A");

        order = mock(RentalOrder.class);
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getUnitId()).thenReturn(UNIT_ID);
        when(order.isExpiredAt(any())).thenReturn(false);

        unit = mock(StorageUnit.class);
        when(unit.getId()).thenReturn(UNIT_ID);
        when(unit.getCode()).thenReturn("A-01");
        when(unit.getFacilityId()).thenReturn(FACILITY_ID);
        when(unit.getStatus()).thenReturn(StorageUnitStatus.AVAILABLE);

        when(invoices.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(units.findByIdForUpdate(UNIT_ID)).thenReturn(Optional.of(unit));
        when(orders.findByUnitId(UNIT_ID)).thenReturn(List.of(order));
        when(invoices.findByOrderIdIn(List.of(ORDER_ID))).thenReturn(List.of(invoice));
        when(sessions.hasOpenSession(anyCollection())).thenReturn(false);
        when(payments.startPayment(eq(INVOICE_ID), any())).thenReturn(PAY_URL);
    }

    @Test
    void branch5ClearPathReturnsPaymentUrl() {
        assertThat(service.startDeposit(INVOICE_ID, "1.2.3.4")).isEqualTo(PAY_URL);
    }

    @Test
    void amountComesFromTheInvoiceNotTheCaller() {
        service.startDeposit(INVOICE_ID, "1.2.3.4");

        verify(payments).startPayment(eq(INVOICE_ID),
                org.mockito.ArgumentMatchers.argThat(
                        request -> request.amount().compareTo(new BigDecimal("10000.00")) == 0));
    }

    @Test
    void unverifiedEmailIsRejectedBeforeReadingTheInvoice() {
        when(currentAccount.isCurrentAccountVerified()).thenReturn(false);

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(EmailNotVerifiedException.class);
        verifyNoInteractions(invoices);
    }

    @Test
    void someoneElsesInvoiceLooksLikeItDoesNotExist() {
        when(invoice.belongsTo(CUSTOMER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void branch1ExpiredOrderIsRejectedBeforeTouchingUnit() {
        when(order.isExpiredAt(any())).thenReturn(true);

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(DepositNotAllowedException.class)
                .hasMessageContaining("Đơn thuê kho đã hết hạn");
        // Chưa đụng tới khoang nên hóa đơn phải còn nguyên hiệu lực.
        verifyNoInteractions(invoiceVoiding);
        verifyNoInteractions(units);
    }

    @Test
    void branch2ReservedUnitVoidsInvoiceAndNotifiesManager() {
        when(unit.getStatus()).thenReturn(StorageUnitStatus.RESERVED);

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(DepositNotAllowedException.class)
                .hasMessageContaining("đã được đặt cọc bởi khách hàng khác");

        verify(invoiceVoiding).voidForUnavailableUnit(INVOICE_ID, "A-01", FACILITY_ID, CUSTOMER_ID);
        verifyNoInteractions(payments);
    }

    @Test
    void branch3MaintenanceUnitVoidsInvoiceAndNotifiesManager() {
        when(unit.getStatus()).thenReturn(StorageUnitStatus.MAINTENANCE);

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(DepositNotAllowedException.class)
                .hasMessageContaining("hiện không khả dụng");

        verify(invoiceVoiding).voidForUnavailableUnit(INVOICE_ID, "A-01", FACILITY_ID, CUSTOMER_ID);
    }

    @Test
    void branch4OpenSessionOnUnitBlocksWithoutSideEffects() {
        when(sessions.hasOpenSession(List.of(INVOICE_ID))).thenReturn(true);

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(DepositNotAllowedException.class)
                .hasMessageContaining("đang trong quá trình xử lý thanh toán");

        verifyNoInteractions(invoiceVoiding);
        verifyNoInteractions(payments);
    }

    @Test
    void lockIsCheckedPerUnitNotPerInvoice() {
        RentalOrder otherOrder = mock(RentalOrder.class);
        when(otherOrder.getId()).thenReturn(201L);
        Invoice otherInvoice = mock(Invoice.class);
        when(otherInvoice.getId()).thenReturn(101L);
        when(orders.findByUnitId(UNIT_ID)).thenReturn(List.of(order, otherOrder));
        when(invoices.findByOrderIdIn(List.of(ORDER_ID, 201L)))
                .thenReturn(List.of(invoice, otherInvoice));

        service.startDeposit(INVOICE_ID, "1.2.3.4");

        // Hóa đơn của khách khác trên cùng khoang cũng phải nằm trong phép kiểm phiên.
        verify(sessions).hasOpenSession(List.of(INVOICE_ID, 101L));
    }

    @Test
    void paidInvoiceDoesNotOpenANewSession() {
        when(invoice.isUnpaid()).thenReturn(false);

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(DepositNotAllowedException.class)
                .hasMessageContaining("không ở trạng thái chờ thanh toán cọc");
        verifyNoInteractions(payments);
    }

    @Test
    void nonDepositInvoiceIsRejected() {
        when(invoice.getType()).thenReturn("Rental");

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(DepositNotAllowedException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void orderWithoutAnExistingUnitIsRejected() {
        when(order.getUnitId()).thenReturn(UNIT_ID);
        when(units.findByIdForUpdate(UNIT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(DepositNotAllowedException.class)
                .hasMessageContaining("hiện không khả dụng");
    }

    @Test
    void txnRefIsAlphanumericAndDerivedFromTheInvoice() {
        service.startDeposit(INVOICE_ID, "1.2.3.4");

        verify(payments).startPayment(anyLong(), org.mockito.ArgumentMatchers.argThat(request ->
                request.txnRef().startsWith("INV" + INVOICE_ID + "T")
                        && request.txnRef().matches("[A-Za-z0-9]{1,100}")));
    }

    @Test
    void expiryIsEvaluatedAgainstThePassedInInstant() {
        RentalOrder realOrder = mock(RentalOrder.class);
        when(realOrder.isExpiredAt(any())).thenAnswer(call -> {
            Instant now = call.getArgument(0);
            return now.isAfter(Instant.now().minus(1, ChronoUnit.MINUTES));
        });
        when(realOrder.getUnitId()).thenReturn(UNIT_ID);
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(realOrder));

        assertThatThrownBy(() -> service.startDeposit(INVOICE_ID, "1.2.3.4"))
                .isInstanceOf(DepositNotAllowedException.class);
    }
}
