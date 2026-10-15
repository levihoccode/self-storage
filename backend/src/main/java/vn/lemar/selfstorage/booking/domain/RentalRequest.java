package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu đặt kho từ form public (B1, spec draft.md §1.1).
 * Kiểu trạng thái + ràng buộc khớp CHECK trong V1 / db-table-draft.md.
 * Khóa ngoại chỉ giữ id thô (không @ManyToOne module chéo).
 */
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "rental_requests")
public class RentalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "normalized_customer_email", nullable = false, length = 255)
    private String normalizedCustomerEmail;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "unit_type_id", nullable = false)
    private Long unitTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private Integer period;

    @Column(name = "unit_id")
    private Long unitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RentalRequestStatus status = RentalRequestStatus.Pending;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "reject_reason", length = 50)
    @Enumerated(EnumType.STRING)
    private RentalRequestRejectReason rejectReason;

    @Column(name = "reject_note", columnDefinition = "text")
    private String rejectNote;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public RentalRequest(Long facilityId, String customerName, String normalizedCustomerEmail,
                         String customerPhone, Long unitTypeId, LocalDate startDate, Integer period,
                         Instant expiresAt) {
        this.facilityId = facilityId;
        this.customerName = customerName;
        this.normalizedCustomerEmail = normalizedCustomerEmail;
        this.customerPhone = customerPhone;
        this.unitTypeId = unitTypeId;
        this.startDate = startDate;
        this.period = period;
        this.expiresAt = expiresAt;
    }

    /**
     * FM duyệt yêu cầu: gán khoang + chuyển Pending sang Approved (B1, spec draft.md §1.1).
     * Caller (service) phải kiểm tra Pending + khoang Available trước — method này chỉ đổi trạng
     * thái, không tự quyết định, để lỗi 409 được ném nhất quán từ service.
     */
    public void approve(Long unitId, Instant respondedAt, Instant expiresAt) {
        this.unitId = unitId;
        this.status = RentalRequestStatus.Approved;
        this.respondedAt = respondedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * FM từ chối yêu cầu: chuyển Pending sang Rejected (B1 phần 4, spec draft.md §1.1).
     * Caller (service) phải kiểm tra Pending + reason/note trước — method này chỉ đổi trạng thái.
     */
    public void reject(RentalRequestRejectReason reason, String note, Instant respondedAt) {
        this.status = RentalRequestStatus.Rejected;
        this.rejectReason = reason;
        this.rejectNote = note;
        this.respondedAt = respondedAt;
    }
}
