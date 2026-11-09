package vn.lemar.selfstorage.booking.application;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.lemar.selfstorage.booking.application.event.RefundAwaitingConfirmation;
import vn.lemar.selfstorage.booking.application.event.RefundDisputed;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.notification.application.NotificationRecipient;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.NotificationType;

/** Gửi thông báo hoàn tiền sau khi transaction nghiệp vụ đã commit. */
@Component
public class RefundNotificationListener {

    private final AccountQueryService accounts;
    private final NotificationService notifications;

    public RefundNotificationListener(AccountQueryService accounts, NotificationService notifications) {
        this.accounts = accounts;
        this.notifications = notifications;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAwaitingConfirmation(RefundAwaitingConfirmation event) {
        String email = accounts.requireEmailById(event.customerId());
        notifications.notify(event.customerId(), email, NotificationRecipient.CUSTOMER,
                NotificationType.REFUND_AWAITING_CONFIRMATION,
                Map.of("orderCode", orderCode(event.orderId(), event.invoiceCode()),
                        "amount", event.amount().toPlainString()),
                event.orderId());

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisputed(RefundDisputed event) {
        String email = accounts.requireEmailById(event.customerId());
        notifications.notify(event.customerId(), email, NotificationRecipient.CUSTOMER,
                NotificationType.REFUND_DISPUTE_RECORDED,
                Map.of("orderCode", orderCode(event.orderId(), event.invoiceCode()),
                        "amount", event.amount().toPlainString()),
                event.orderId());
    }

    private static String orderCode(Long orderId, String invoiceCode) {
        return orderId == null ? invoiceCode : String.valueOf(orderId);
    }
}
