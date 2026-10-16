package vn.lemar.selfstorage.booking.application;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.booking.application.exception.DepositNotAllowedException;
import vn.lemar.selfstorage.booking.application.exception.EmailNotVerifiedException;
import vn.lemar.selfstorage.booking.application.exception.InvoiceNotFoundException;
import vn.lemar.selfstorage.booking.domain.Invoice;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.domain.StorageUnit;
import vn.lemar.selfstorage.booking.repository.InvoiceRepository;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.booking.repository.StorageUnitRepository;
import vn.lemar.selfstorage.identity.application.CurrentAccountProvider;
import vn.lemar.selfstorage.payment.application.PaymentService;
import vn.lemar.selfstorage.payment.application.PaymentSessionQuery;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;

/**
 * Đặt cọc giữ kho — spec `draft.md` §1.4.
 *
 * <p>Năm nhánh kiểm chạy đúng thứ tự spec và dừng ở nhánh đầu tiên không đạt. Thứ tự quan trọng:
 * đơn quá hạn phải chặn trước khi đụng tới khoang, còn khóa phiên phải kiểm sau cùng để không
 * báo "đang xử lý" cho một khoang thật ra đã `Reserved`.
 *
 * <p>Service này <strong>không</strong> tự ghi nhận đã thu tiền. Nó chỉ mở phiên; việc chốt
 * thuộc {@link DepositPaymentListener} khi cổng xác nhận qua IPN.
 */
@Service
public class DepositPaymentService {

    private static final String DEPOSIT_TYPE = "Deposit";

    private static final DateTimeFormatter TXN_SUFFIX =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final String TXN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TXN_SUFFIX_LENGTH = 5;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    // Nguyên văn spec §1.4 — FE hiển thị thẳng cho khách nên không được diễn đạt lại.
    private static final String ORDER_EXPIRED =
            "Đơn thuê kho đã hết hạn. Vui lòng liên hệ cơ sở để được hỗ trợ.";
    private static final String UNIT_TAKEN =
            "Khoang chứa đã được đặt cọc bởi khách hàng khác. Hóa đơn này đã hết hiệu lực.";
    private static final String UNIT_MAINTENANCE =
            "Khoang chứa hiện không khả dụng. Vui lòng liên hệ cơ sở để được hỗ trợ.";
    private static final String UNIT_BUSY =
            "Khoang chứa này hiện đang trong quá trình xử lý thanh toán (đặt cọc / gia hạn) "
                    + "bởi một khách hàng khác. Vui lòng quay lại thử lại sau ít phút "
                    + "hoặc chọn khoang chứa khác!";

    private final InvoiceRepository invoices;
    private final RentalOrderRepository orders;
    private final StorageUnitRepository units;
    private final PaymentService payments;
    private final PaymentSessionQuery sessions;
    private final CurrentAccountProvider currentAccount;
    private final InvoiceVoidingService invoiceVoiding;

    public DepositPaymentService(InvoiceRepository invoices, RentalOrderRepository orders,
            StorageUnitRepository units, PaymentService payments, PaymentSessionQuery sessions,
            CurrentAccountProvider currentAccount, InvoiceVoidingService invoiceVoiding) {
        this.invoices = invoices;
        this.orders = orders;
        this.units = units;
        this.payments = payments;
        this.sessions = sessions;
        this.currentAccount = currentAccount;
        this.invoiceVoiding = invoiceVoiding;
    }

    /**
     * Mở một phiên thanh toán cho hóa đơn cọc và trả URL để FE chuyển hướng khách sang VNPay.
     *
     * @throws InvoiceNotFoundException   hóa đơn không tồn tại hoặc không thuộc người gọi
     * @throws EmailNotVerifiedException  khách chưa xác minh email
     * @throws DepositNotAllowedException rơi vào một trong bốn nhánh từ chối của spec §1.4
     */
    @Transactional
    public String startDeposit(Long invoiceId, String clientIp) {
        if (!currentAccount.isCurrentAccountVerified()) {
            throw new EmailNotVerifiedException();
        }
        Long actorId = currentAccount.currentAccountId();

        Invoice invoice = invoices.findById(invoiceId)
                .filter(found -> found.belongsTo(actorId))
                .orElseThrow(InvoiceNotFoundException::new);

        if (!invoice.isUnpaid() || !DEPOSIT_TYPE.equals(invoice.getType())) {
            // Hóa đơn đã thanh toán/hủy, hoặc không phải hóa đơn cọc — không có đường đi tiếp.
            throw new DepositNotAllowedException("Hóa đơn này không ở trạng thái chờ thanh toán cọc.");
        }

        RentalOrder order = orders.findById(invoice.getOrderId())
                .orElseThrow(InvoiceNotFoundException::new);

        // (1) Đơn quá hạn — chặn trước khi đụng tới khoang.
        if (order.isExpiredAt(Instant.now())) {
            throw new DepositNotAllowedException(ORDER_EXPIRED);
        }

        // Khoá khoang trước mọi phép kiểm liên quan tới nó — xem javadoc findByIdForUpdate.
        StorageUnit unit = units.findByIdForUpdate(order.getUnitId())
                .orElseThrow(() -> new DepositNotAllowedException(UNIT_MAINTENANCE));

        // (2)(3) Khoang không còn dùng được — hóa đơn hết hiệu lực, FM phải đề xuất khoang khác.
        switch (unit.getStatus()) {
            case RESERVED, RENTED -> {
                voidInvoice(invoice, unit, actorId);
                throw new DepositNotAllowedException(UNIT_TAKEN);
            }
            case MAINTENANCE -> {
                voidInvoice(invoice, unit, actorId);
                throw new DepositNotAllowedException(UNIT_MAINTENANCE);
            }
            default -> {
                // Available — đi tiếp.
            }
        }

        // (4) Khoang đang có phiên thanh toán khác còn mở. Kiểm theo khoang, không theo hóa đơn:
        // hai khách khác nhau trả cho hai hóa đơn khác nhau vẫn tranh cùng một khoang.
        if (sessions.hasOpenSession(invoiceIdsOnUnit(unit.getId()))) {
            throw new DepositNotAllowedException(UNIT_BUSY);
        }

        // (5) Thông thoáng — mở phiên. Số tiền đọc từ hóa đơn trong DB, không tin tham số caller.
        PaymentRequest request = new PaymentRequest(
                nextTxnRef(invoice.getId()), invoice.getAmount(), invoice.getTitle(), clientIp);
        return payments.startPayment(invoice.getId(), request);
    }

    /** Uỷ cho bean riêng để bản ghi `Canceled` commit độc lập với việc ném 409. */
    private void voidInvoice(Invoice invoice, StorageUnit unit, Long actorId) {
        invoiceVoiding.voidForUnavailableUnit(
                invoice.getId(), unit.getCode(), unit.getFacilityId(), actorId);
    }

    private List<Long> invoiceIdsOnUnit(Long unitId) {
        List<Long> orderIds = orders.findByUnitId(unitId).stream().map(RentalOrder::getId).toList();
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return invoices.findByOrderIdIn(orderIds).stream().map(Invoice::getId).toList();
    }

    /**
     * VNPay yêu cầu {@code vnp_TxnRef} không trùng trong ngày và chỉ gồm chữ/số.
     *
     * <p>Mốc thời gian chỉ tới giây, nên hai lần bấm trong cùng một giây sẽ ra mã y hệt và vỡ
     * unique constraint — khách nhận 500 thay vì lỗi nghiệp vụ. Thêm hậu tố ngẫu nhiên để mã
     * duy nhất kể cả khi trùng giây.
     */
    private String nextTxnRef(Long invoiceId) {
        return "INV" + invoiceId + "T" + TXN_SUFFIX.format(Instant.now()) + randomSuffix();
    }

    private String randomSuffix() {
        StringBuilder suffix = new StringBuilder(TXN_SUFFIX_LENGTH);
        for (int i = 0; i < TXN_SUFFIX_LENGTH; i++) {
            suffix.append(TXN_ALPHABET.charAt(RANDOM.nextInt(TXN_ALPHABET.length())));
        }
        return suffix.toString();
    }
}
