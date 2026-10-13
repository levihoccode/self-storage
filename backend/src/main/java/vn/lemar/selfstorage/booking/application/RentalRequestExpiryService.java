package vn.lemar.selfstorage.booking.application;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.booking.repository.RentalRequestRepository;

/**
 * Cron {@code draft.md} › Scheduled Jobs: {@code RentalRequest} quá {@code expires_at} mà vẫn
 * {@code Pending}/{@code Approved} → {@code Expired}. Gọi từ module scheduler.
 */
@Service
public class RentalRequestExpiryService {

    private final RentalRequestRepository requestRepository;

    public RentalRequestExpiryService(RentalRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    /** @return số request vừa chuyển {@code Expired} */
    @Transactional
    public int expireOverdue() {
        return requestRepository.expireOverdue(Instant.now());
    }
}
