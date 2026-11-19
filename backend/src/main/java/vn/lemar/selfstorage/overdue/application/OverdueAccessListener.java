package vn.lemar.selfstorage.overdue.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import vn.lemar.selfstorage.booking.application.AppointmentArrived;
import vn.lemar.selfstorage.booking.application.ContractInvoicePaid;

/**
 * Mở lại truy cập đang tạm khoá ngay khi đủ điều kiện, không đợi cron mỗi giờ:
 * <ul>
 *   <li>hoá đơn của hợp đồng vừa trả (phí quá hạn, gia hạn, nợ gộp trả kho) — hết nợ + hết quá hạn, hoặc khách đã
 *       đến buổi trả kho và hết nợ, thì mở;</li>
 *   <li>FS vừa xác nhận khách đến một buổi hẹn — hợp đồng quá hạn có buổi trả kho khách đã đến: chốt phí, gộp nợ
 *       thành một hoá đơn; không còn nợ thì mở để dọn đồ.</li>
 * </ul>
 * Chạy {@code AFTER_COMMIT}: gia hạn / mốc khách đến đã ghi xong mới xét. Mỗi hợp đồng một transaction
 * {@code REQUIRES_NEW} — trong pha AFTER_COMMIT, gọi thẳng method {@code @Transactional} (REQUIRED) sẽ nhập vào
 * transaction đã commit và thay đổi không được lưu. Lỗi chỉ log — cron mỗi giờ vẫn là đường dự phòng.
 */
@Component
class OverdueAccessListener {

    private static final Logger LOG = LoggerFactory.getLogger(OverdueAccessListener.class);

    private final OverdueService service;
    private final TransactionTemplate newTx;

    OverdueAccessListener(OverdueService service, PlatformTransactionManager transactionManager) {
        this.service = service;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ContractInvoicePaid event) {
        if (event.contractId() != null) {
            prepareThenRestore(event.contractId(), "sau thanh toán");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AppointmentArrived event) {
        // Không có liên kết ngược lịch → hợp đồng ở đây; số hợp đồng đang tạm khoá nhỏ nên xét lại tất cả.
        java.util.List<Long> suspended = newTx.execute(status -> service.suspendedIds());
        for (Long contractId : suspended == null ? java.util.List.<Long>of() : suspended) {
            prepareThenRestore(contractId, "khi khách đến buổi hẹn");
        }
    }

    /** Gộp nợ trước khi dọn đồ (nếu khách đã đến buổi trả kho) rồi xét mở lại — mỗi bước một transaction. */
    private void prepareThenRestore(Long contractId, String when) {
        try {
            newTx.executeWithoutResult(status -> service.prepareMoveOut(contractId));
        } catch (RuntimeException e) {
            LOG.warn("Gộp nợ trả kho {} — hợp đồng {} lỗi: {}", when, contractId, e.getMessage());
        }
        try {
            newTx.executeWithoutResult(status -> service.restoreIfCleared(contractId));
        } catch (RuntimeException e) {
            LOG.warn("Mở lại truy cập {} — hợp đồng {} lỗi: {}", when, contractId, e.getMessage());
        }
    }
}
