package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.PaymentUrlCache;
import vn.lemar.selfstorage.payment.application.dto.GatewayNotification;
import vn.lemar.selfstorage.payment.application.dto.PaymentNotificationResult;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;
import vn.lemar.selfstorage.payment.application.event.PaymentFailed;
import vn.lemar.selfstorage.payment.application.event.PaymentSucceeded;
import vn.lemar.selfstorage.payment.config.VnPayProperties;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;
import vn.lemar.selfstorage.payment.domain.PaymentTransaction;
import vn.lemar.selfstorage.payment.repository.PaymentTransactionRepository;

/** Luồng IPN — nguồn xác nhận thanh toán duy nhất, nên mọi nhánh rẽ đều phải có test. */
class PaymentServiceTest {

    private static final String TXN_REF = "160932558";
    private static final String TMN_CODE = "TESTTMN1";

    private PaymentGateway gateway;
    private PaymentTransactionRepository repository;
    private ApplicationEventPublisher events;
    private AuditRecorder audit;
    private PaymentUrlCache urlCache;
    private PaymentService service;
    private PaymentTransaction transaction;

    @BeforeEach
    void setUp() {
        gateway = mock(PaymentGateway.class);
        repository = mock(PaymentTransactionRepository.class);
        events = mock(ApplicationEventPublisher.class);
        audit = mock(AuditRecorder.class);
        urlCache = mock(PaymentUrlCache.class);
        service = new PaymentService(gateway, repository,
                new VnPayProperties(TMN_CODE, "secret", "https://pay", "https://return",
                        "https://query"), events,
                new ObjectMapper(), audit, urlCache);

        transaction = new PaymentTransaction(7L, TXN_REF, new BigDecimal("10000"), "Thanh toan");
        when(gateway.verifySignature(any())).thenReturn(true);
        when(repository.findByVnpTxnRef(TXN_REF)).thenReturn(Optional.of(transaction));
    }

    private Map<String, String> ipn() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", TXN_REF);
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_Amount", "1000000"); // 10.000d da nhan 100
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "15690354");
        params.put("vnp_PayDate", "20260929161520");
        params.put("vnp_SecureHash", "chu-ky-da-duoc-mock-verify");
        return params;
    }

    @Test
    void recordsSuccessAndEmitsEventOnFirstIpn() {
        var response = service.handleIpn(ipn());

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.getGatewayTransactionNo()).isEqualTo("15690354");
        verify(events).publishEvent(any(PaymentSucceeded.class));
    }

    @Test
    void startPaymentCachesTheUrlForBackButton() {
        when(gateway.buildPaymentUrl(any(), any(Instant.class))).thenReturn("https://pay/session");

        String url = service.startPayment(7L, new PaymentRequest(
                "TXN", new BigDecimal("10000"), "Thanh toan", "127.0.0.1"));

        assertThat(url).isEqualTo("https://pay/session");
        verify(urlCache).put(7L, "https://pay/session");
    }

    // Resume đọc payment_url đã lưu thay vì dựng lại URL (chốt ở #184: có cổng không cho tạo lại đơn
    // với cùng mã) — test cũ "rebuild" được thay bằng hai test dưới.
    @Test
    void resumeReturnsTheStoredUrlWithoutCreatingANewTransaction() {
        transaction.rememberPaymentUrl("https://pay/stored");
        when(repository.findFirstByInvoiceIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(PaymentStatus.class),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(Optional.of(transaction));

        assertThat(service.resumePayment(7L, "203.0.113.9")).contains("https://pay/stored");

        verify(gateway, never()).buildPaymentUrl(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Instant.class));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(urlCache).put(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("https://pay/stored"),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void resumeWithoutStoredUrlReturnsEmpty() {
        when(repository.findFirstByInvoiceIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(PaymentStatus.class),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(Optional.of(transaction));

        assertThat(service.resumePayment(7L, "203.0.113.9")).isEmpty();
    }

    @Test
    void startPaymentStoresTheUrlOnTheTransaction() {
        when(gateway.createTxnRef(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.buildPaymentUrl(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn("https://pay/new");
        org.mockito.ArgumentCaptor<PaymentTransaction> saved =
                org.mockito.ArgumentCaptor.forClass(PaymentTransaction.class);

        service.startPayment(7L, new PaymentRequest("TXN-NEW", new BigDecimal("10000"), "Dat coc", "1.2.3.4"));

        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPaymentUrl()).isEqualTo("https://pay/new");
    }

    @Test
    void settledTransactionForgetsItsUrlAndCache() {
        transaction.rememberPaymentUrl("https://pay/stored");
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(transaction));

        service.reconcileFailure(1L, "đối soát: failed", "{}");

        assertThat(transaction.getPaymentUrl()).isNull();
        verify(urlCache).evict(7L);
    }

    @Test
    void expiredUrlsAreClearedThroughTheRepository() {
        when(repository.clearPaymentUrlCreatedBefore(org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(3);

        assertThat(service.forgetExpiredPaymentUrls()).isEqualTo(3);
    }

    @Test
    void reconcileSuccessMarksAPendingTransaction() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(transaction));

        assertThat(service.reconcileSuccess(1L, "GW-RECON", Instant.now(), "{}")).isTrue();

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(events).publishEvent(any(PaymentSucceeded.class));
    }

    @Test
    void reconcileFailureMarksAPendingTransaction() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(transaction));

        assertThat(service.reconcileFailure(1L, "querydr: not found", "{}")).isTrue();

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(events).publishEvent(any(PaymentFailed.class));
    }

    @Test
    void reconcileSkipsATransactionTheIpnAlreadySettled() {
        transaction.markSucceeded("GW-IPN", Instant.now(), "{}");
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(transaction));

        assertThat(service.reconcileSuccess(1L, "GW-RECON", Instant.now(), "{}")).isFalse();
        verify(events, never()).publishEvent(any(PaymentSucceeded.class));
    }

    @Test
    void storesIpnPayloadAsJson() {
        service.handleIpn(ipn());

        assertThat(transaction.getResponsePayload())
                .startsWith("{")
                .contains("\"vnp_TxnRef\":\"160932558\"");
    }

    @Test
    void invalidPayDateFallsBackToIpnArrivalTime() {
        Map<String, String> params = ipn();
        params.put("vnp_PayDate", "khong-phai-ngay");

        var response = service.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.getPaidAt()).isNotNull();
    }

    @Test
    void missingPayDateFallsBackToIpnArrivalTime() {
        Map<String, String> params = ipn();
        params.remove("vnp_PayDate");

        var response = service.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.getPaidAt()).isNotNull();
    }

    @Test
    void fallsBackToToStringWhenJsonSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonGenerationException("boom"));
        PaymentService failingService = new PaymentService(gateway, repository,
                new VnPayProperties(TMN_CODE, "secret", "https://pay", "https://return",
                        "https://query"), events,
                failingMapper, audit, urlCache);
        Map<String, String> params = ipn();

        var response = failingService.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(transaction.getResponsePayload()).isEqualTo(params.toString());
    }

    /** Tiêu chí bắt buộc của A6: VNPay retry tới 10 lần, lần hai không được sinh giao dịch mới. */
    @Test
    void secondIpnDoesNotWriteAgainAndReturns02() {
        service.handleIpn(ipn());
        reset(events);

        var second = service.handleIpn(ipn());

        assertThat(second.rspCode()).isEqualTo("02");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verifyNoInteractions(events);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsWhenSignatureInvalid() {
        when(gateway.verifySignature(any())).thenReturn(false);

        assertThat(service.handleIpn(ipn()).rspCode()).isEqualTo("97");
        verifyNoInteractions(events);
    }

    @Test
    void returns01WhenTransactionNotFound() {
        when(repository.findByVnpTxnRef(TXN_REF)).thenReturn(Optional.empty());

        assertThat(service.handleIpn(ipn()).rspCode()).isEqualTo("01");
    }

    @Test
    void returns01WhenTmnCodeNotOurs() {
        Map<String, String> params = ipn();
        params.put("vnp_TmnCode", "KHACTMN");

        assertThat(service.handleIpn(params).rspCode()).isEqualTo("01");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void returns04WhenAmountMismatchesRecordedTransaction() {
        Map<String, String> params = ipn();
        params.put("vnp_Amount", "9900000");

        assertThat(service.handleIpn(params).rspCode()).isEqualTo("04");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void recordsFailureWhenGatewayReportsNon00() {
        Map<String, String> params = ipn();
        params.put("vnp_ResponseCode", "24"); // khach huy giao dich

        assertThat(service.handleIpn(params).rspCode()).isEqualTo("00");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(events).publishEvent(any(PaymentFailed.class));
    }

    @Test
    void savesPendingTransactionBeforeRedirect() {
        when(gateway.buildPaymentUrl(any(), any(Instant.class))).thenReturn("https://pay?x=1");

        String url = service.startPayment(7L,
                new PaymentRequest("TXN999", new BigDecimal("250000"), "Thanh toan", "127.0.0.1"));

        assertThat(url).isEqualTo("https://pay?x=1");
        verify(repository).save(argThat(saved ->
                saved.isPending() && saved.getVnpTxnRef().equals("TXN999")));
    }

    @Test
    void startPaymentRejectsNullInvoiceId() {
        PaymentRequest request =
                new PaymentRequest("TXN1", new BigDecimal("10000"), "Thanh toan", "127.0.0.1");

        assertThatThrownBy(() -> service.startPayment(null, request))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("invoiceId");
    }

    @Test
    void handlesANormalizedGatewaySuccess() {
        GatewayNotification notification = new GatewayNotification(
                TXN_REF, PaymentStatus.SUCCESS, new BigDecimal("10000"), Instant.now(),
                "ZP123", "{\"status\":1}");

        assertThat(service.handleNotification(notification))
                .isEqualTo(PaymentNotificationResult.ACCEPTED);
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.getGatewayTransactionNo()).isEqualTo("ZP123");
        verify(events).publishEvent(any(PaymentSucceeded.class));
    }

    @Test
    void normalizedNotificationRejectsWrongAmount() {
        GatewayNotification notification = new GatewayNotification(
                TXN_REF, PaymentStatus.SUCCESS, new BigDecimal("9999"), Instant.now(),
                "ZP123", "{}");

        assertThat(service.handleNotification(notification))
                .isEqualTo(PaymentNotificationResult.INVALID_AMOUNT);
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void normalizedNotificationIgnoresProcessingStatus() {
        GatewayNotification notification = new GatewayNotification(
                TXN_REF, PaymentStatus.PENDING, new BigDecimal("10000"), null, null, "{}");

        assertThat(service.handleNotification(notification))
                .isEqualTo(PaymentNotificationResult.IGNORED);
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void normalizedNotificationReportsAnUnknownTransaction() {
        when(repository.findByVnpTxnRef("KHONG-CO")).thenReturn(Optional.empty());

        GatewayNotification notification = new GatewayNotification(
                "KHONG-CO", PaymentStatus.SUCCESS, new BigDecimal("10000"), Instant.now(), "ZP", "{}");

        assertThat(service.handleNotification(notification))
                .isEqualTo(PaymentNotificationResult.NOT_FOUND);
    }
}
