package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import vn.lemar.selfstorage.payment.application.PaymentGateway;
import vn.lemar.selfstorage.payment.application.PaymentService;
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
    private PaymentService service;
    private PaymentTransaction transaction;

    @BeforeEach
    void setUp() {
        gateway = mock(PaymentGateway.class);
        repository = mock(PaymentTransactionRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new PaymentService(gateway, repository,
                new VnPayProperties(TMN_CODE, "secret", "https://pay", "https://return"), events);

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
    void ghiNhanThanhCongVaBanEventOLuotDauTien() {
        var response = service.handleIpn(ipn());

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.getGatewayTransactionNo()).isEqualTo("15690354");
        verify(events).publishEvent(any(PaymentSucceeded.class));
    }

    /** Tiêu chí bắt buộc của A6: VNPay retry tới 10 lần, lần hai không được sinh giao dịch mới. */
    @Test
    void goiIpnLanHaiKhongGhiLaiVaTraVe02() {
        service.handleIpn(ipn());
        reset(events);

        var second = service.handleIpn(ipn());

        assertThat(second.rspCode()).isEqualTo("02");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verifyNoInteractions(events);
        verify(repository, never()).save(any());
    }

    @Test
    void tuChoiKhiSaiChuKy() {
        when(gateway.verifySignature(any())).thenReturn(false);

        assertThat(service.handleIpn(ipn()).rspCode()).isEqualTo("97");
        verifyNoInteractions(events);
    }

    @Test
    void traVe01KhiKhongTimThayGiaoDich() {
        when(repository.findByVnpTxnRef(TXN_REF)).thenReturn(Optional.empty());

        assertThat(service.handleIpn(ipn()).rspCode()).isEqualTo("01");
    }

    @Test
    void traVe01KhiTmnCodeKhongPhaiCuaHeThongNay() {
        Map<String, String> params = ipn();
        params.put("vnp_TmnCode", "KHACTMN");

        assertThat(service.handleIpn(params).rspCode()).isEqualTo("01");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void traVe04KhiSoTienLechVoiGiaoDichDaGhi() {
        Map<String, String> params = ipn();
        params.put("vnp_Amount", "9900000");

        assertThat(service.handleIpn(params).rspCode()).isEqualTo("04");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void ghiNhanThatBaiKhiCongBaoMaKhac00() {
        Map<String, String> params = ipn();
        params.put("vnp_ResponseCode", "24"); // khach huy giao dich

        assertThat(service.handleIpn(params).rspCode()).isEqualTo("00");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(events).publishEvent(any(PaymentFailed.class));
    }

    @Test
    void luuGiaoDichPendingTruocKhiRedirect() {
        when(gateway.buildPaymentUrl(any())).thenReturn("https://pay?x=1");

        String url = service.startPayment(7L,
                new PaymentRequest("TXN999", new BigDecimal("250000"), "Thanh toan", "127.0.0.1"));

        assertThat(url).isEqualTo("https://pay?x=1");
        verify(repository).save(argThat(saved ->
                saved.isPending() && saved.getVnpTxnRef().equals("TXN999")));
    }
}
