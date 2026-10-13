package vn.lemar.selfstorage.scheduler.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.lemar.selfstorage.booking.application.RentalRequestExpiryService;

/**
 * Job hằng ngày ({@code draft.md} › Scheduled Jobs): {@code RentalRequest} quá {@code expires_at}
 * mà vẫn {@code Pending}/{@code Approved} → {@code Expired}. Nghiệp vụ nằm ở booking, job chỉ gọi.
 *
 * <p>Chưa dùng ShedLock: thao tác là một conditional update, chạy trùng trên nhiều instance chỉ
 * cập nhật 0 row ở lần sau.
 */
@Component
public class RentalRequestExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(RentalRequestExpiryJob.class);

    private final RentalRequestExpiryService expiryService;

    public RentalRequestExpiryJob(RentalRequestExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    @Scheduled(cron = "${app.scheduler.rental-request-expiry-cron:0 5 0 * * *}",
            zone = "${app.scheduler.zone:Asia/Ho_Chi_Minh}")
    public void run() {
        int expired = expiryService.expireOverdue();
        LOG.info("RentalRequest expiry job: {} request chuyển Expired", expired);
    }
}
