package vn.lemar.selfstorage.booking.application;

import java.time.Instant;
import vn.lemar.selfstorage.booking.domain.ProposalFeedback;

/**
 * Ảnh chụp một proposal sau thao tác — controller (PR sau) dựng response từ đây.
 *
 * @param status giá trị PascalCase như trong DB ({@code Pending}, {@code Agreed}, ...)
 */
public record ProposalView(Long id, Long orderId, Long customerId, Long unitId, String status, String note,
                           Instant createdAt) {

    static ProposalView of(ProposalFeedback proposal) {
        return new ProposalView(proposal.getId(), proposal.getOrderId(), proposal.getCustomerId(),
                proposal.getUnitId(), proposal.getStatus().dbValue(), proposal.getNote(), proposal.getCreatedAt());
    }
}
