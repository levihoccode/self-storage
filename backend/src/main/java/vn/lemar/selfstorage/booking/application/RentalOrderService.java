package vn.lemar.selfstorage.booking.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.booking.application.exception.ProposalConflictException;
import vn.lemar.selfstorage.booking.application.exception.RentalOrderNotFoundException;
import vn.lemar.selfstorage.booking.domain.RentalOrder;
import vn.lemar.selfstorage.booking.repository.RentalOrderRepository;
import vn.lemar.selfstorage.identity.application.Access;

/**
 * Thao tác của FM trên đơn thuê ngoài luồng proposal — #142: khách không phản hồi proposal, FM liên hệ ngoài
 * hệ thống không được thì huỷ đơn ({@code specs/draft.md} › 1.3).
 */
@Service
public class RentalOrderService {

    static final String PERMISSION_CANCEL = "rental_order.cancel";
    static final String CANCEL_REASON_NO_LONGER_NEEDED = "Khách không còn nhu cầu";

    private final RentalOrderRepository orderRepository;
    private final Access access;
    private final RentalOrderCancellation orderCancellation;

    RentalOrderService(RentalOrderRepository orderRepository, Access access,
                       RentalOrderCancellation orderCancellation) {
        this.orderRepository = orderRepository;
        this.access = access;
        this.orderCancellation = orderCancellation;
    }

    /**
     * FM huỷ đơn với lý do cố định {@value #CANCEL_REASON_NO_LONGER_NEEDED}, chạy đủ Cancellation Cascade.
     * Quyền {@code rental_order.cancel} theo cơ sở của đơn; đơn đã đóng → 409.
     */
    @Transactional
    public RentalOrderView cancelByFacilityManager(Long orderId, Long actorAccountId) {
        Long facilityId = orderRepository.findFacilityId(orderId).orElseThrow(RentalOrderNotFoundException::new);
        access.can(PERMISSION_CANCEL, facilityId);

        RentalOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow(RentalOrderNotFoundException::new);
        if (order.getStatus().isClosed()) {
            throw new ProposalConflictException("Đơn thuê đã đóng, không thể huỷ");
        }
        orderCancellation.cancel(order, CANCEL_REASON_NO_LONGER_NEEDED, actorAccountId);
        return RentalOrderView.of(order);
    }
}
