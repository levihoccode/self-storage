package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Đề xuất khoang và phản hồi của khách ({@code specs/db-table-draft.md} › ProposalFeedback).
 *
 * <p>Mỗi lần đề xuất lại tạo bản ghi mới, bản ghi cũ giữ nguyên làm lịch sử. Chỉ proposal
 * {@code Pending} mới chuyển trạng thái được — caller kiểm {@link #isPending()} trước khi gọi.
 * Cột {@code expires_at} không map: spec NOTES 1.3 nói ProposalFeedback không đặt TTL.
 */
@Entity
@Table(name = "proposal_feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Convert(converter = ProposalStatusConverter.class)
    @Column(nullable = false, length = 20)
    private ProposalStatus status;

    @Column
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private ProposalFeedback(Long orderId, Long customerId, Long unitId) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.unitId = unitId;
        this.status = ProposalStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /** Đề xuất mới cho đơn — luôn bắt đầu ở {@code Pending}. */
    public static ProposalFeedback propose(RentalOrder order, Long unitId) {
        return new ProposalFeedback(order.getId(), order.getCustomerId(), unitId);
    }

    public boolean isPending() {
        return status == ProposalStatus.PENDING;
    }

    public void agree() {
        requirePending();
        this.status = ProposalStatus.AGREED;
    }

    public void reject(String rejectNote) {
        requirePending();
        this.status = ProposalStatus.REJECTED;
        this.note = rejectNote;
    }

    /** Spec 1.3: khách bấm đồng ý nhưng khoang không còn {@code Available}. */
    public void expire() {
        requirePending();
        this.status = ProposalStatus.EXPIRED;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("Proposal " + id + " không còn Pending: " + status);
        }
    }
}
