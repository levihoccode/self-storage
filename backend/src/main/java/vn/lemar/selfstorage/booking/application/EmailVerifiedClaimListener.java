package vn.lemar.selfstorage.booking.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.lemar.selfstorage.identity.application.EmailVerified;

/**
 * Trigger 2 của B2 — claim {@code RentalRequest} sau khi customer xác minh email (spec 1.2 Case A).
 *
 * <p>Chạy <b>sau khi</b> transaction xác minh đã commit, trong transaction riêng của
 * {@link RentalRequestConversionService#claimForVerifiedAccount}: claim lỗi không làm hỏng việc xác
 * minh email; lỗi chỉ log (request vẫn {@code Approved}, không tự claim lại).
 */
@Component
class EmailVerifiedClaimListener {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerifiedClaimListener.class);

    private final RentalRequestConversionService conversionService;

    EmailVerifiedClaimListener(RentalRequestConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onEmailVerified(EmailVerified event) {
        try {
            List<ConversionResult> results = conversionService.claimForVerifiedAccount(event.accountId());
            LOG.info("Claim sau xác minh email account {}: {}", event.accountId(), results);
        } catch (RuntimeException e) {
            LOG.error("Claim RentalRequest sau xác minh email thất bại cho account {}", event.accountId(), e);
        }
    }
}
