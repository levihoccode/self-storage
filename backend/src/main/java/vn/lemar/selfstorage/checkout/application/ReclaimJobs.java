package vn.lemar.selfstorage.checkout.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cron mỗi giờ: báo khách (+ FM) đồ lưu giữ sắp tới hạn thanh lý — mỗi lô một transaction, một lần. */
@Component
class ReclaimJobs {

    private static final Logger LOG = LoggerFactory.getLogger(ReclaimJobs.class);

    private final ReclaimService reclaimService;

    ReclaimJobs(ReclaimService reclaimService) {
        this.reclaimService = reclaimService;
    }

    @Scheduled(
            fixedDelayString = "${checkout.liquidation-notice.fixed-delay-ms:3600000}",
            initialDelayString = "${checkout.liquidation-notice.initial-delay-ms:180000}")
    public void notifyLiquidation() {
        int sent = 0;
        for (Long id : reclaimService.liquidationNoticeIds()) {
            try {
                if (reclaimService.notifyLiquidation(id)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                LOG.warn("Báo sắp thanh lý lô đồ {} lỗi: {}", id, e.getMessage());
            }
        }
        if (sent > 0) {
            LOG.info("Đã báo sắp thanh lý {} lô đồ lưu giữ", sent);
        }
    }
}
