package vn.lemar.selfstorage.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lô đồ FM thu hồi từ khoang của hợp đồng quá hạn kéo dài (V40). Vòng đời: {@code Holding} (lưu giữ tới
 * {@code hold_until}) → {@code Returned} (khách trả hết nợ + phí lưu giữ, FM trả đồ) hoặc {@code Liquidated} (hết hạn
 * lưu giữ, FM thanh lý; tiền thanh lý trừ nợ, dư giữ hộ khách). Mỗi hợp đồng tối đa một lô.
 */
@Entity
@Table(name = "reclaimed_goods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReclaimedGoods {

    public static final String HOLDING = "Holding";
    public static final String RETURNED = "Returned";
    public static final String LIQUIDATED = "Liquidated";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "reclaimed_at", nullable = false)
    private Instant reclaimedAt;

    @Column(name = "reclaimed_by", nullable = false)
    private Long reclaimedBy;

    @Column(name = "witness_staff_id", nullable = false)
    private Long witnessStaffId;

    @Column(name = "inventory_note", nullable = false)
    private String inventoryNote;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "photos", columnDefinition = "text[]")
    private List<String> photos;

    @Column(name = "storage_location", nullable = false)
    private String storageLocation;

    @Column(name = "hold_until", nullable = false)
    private LocalDate holdUntil;

    @Column(name = "liquidation_notice_at")
    private Instant liquidationNoticeAt;

    @Column(name = "return_requested_at")
    private Instant returnRequestedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "returned_by")
    private Long returnedBy;

    @Column(name = "liquidated_at")
    private Instant liquidatedAt;

    @Column(name = "liquidated_by")
    private Long liquidatedBy;

    @Column(name = "proceeds_amount")
    private BigDecimal proceedsAmount;

    @Column(name = "liquidation_note")
    private String liquidationNote;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "liquidation_photos", columnDefinition = "text[]")
    private List<String> liquidationPhotos;

    @Column(name = "surplus_amount")
    private BigDecimal surplusAmount;

    @Column(name = "surplus_refunded_at")
    private Instant surplusRefundedAt;

    @Column(name = "surplus_refunded_by")
    private Long surplusRefundedBy;

    public static ReclaimedGoods reclaim(Long contractId, Long facilityId, String unitCode, Long fmId,
                                         Long witnessStaffId, String inventoryNote, List<String> photos,
                                         String storageLocation, LocalDate holdUntil, Instant now) {
        ReclaimedGoods goods = new ReclaimedGoods();
        goods.contractId = Objects.requireNonNull(contractId, "contractId");
        goods.facilityId = Objects.requireNonNull(facilityId, "facilityId");
        goods.unitCode = unitCode == null ? "" : unitCode;
        goods.status = HOLDING;
        goods.reclaimedAt = now;
        goods.reclaimedBy = Objects.requireNonNull(fmId, "fmId");
        goods.witnessStaffId = Objects.requireNonNull(witnessStaffId, "witnessStaffId");
        goods.inventoryNote = Objects.requireNonNull(inventoryNote, "inventoryNote");
        goods.photos = List.copyOf(photos);
        goods.storageLocation = Objects.requireNonNull(storageLocation, "storageLocation");
        goods.holdUntil = Objects.requireNonNull(holdUntil, "holdUntil");
        goods.liquidationPhotos = List.of();
        return goods;
    }

    public boolean holding() {
        return HOLDING.equals(status);
    }

    public void requestReturn(Instant now) {
        requireHolding();
        this.returnRequestedAt = now;
    }

    public void markLiquidationNoticeSent(Instant now) {
        this.liquidationNoticeAt = now;
    }

    public void markReturned(Long fmId, Instant now) {
        requireHolding();
        this.status = RETURNED;
        this.returnedBy = fmId;
        this.returnedAt = now;
    }

    public void liquidate(Long fmId, BigDecimal proceeds, String note, List<String> photos, BigDecimal surplus,
                          Instant now) {
        requireHolding();
        this.status = LIQUIDATED;
        this.liquidatedBy = fmId;
        this.liquidatedAt = now;
        this.proceedsAmount = proceeds;
        this.liquidationNote = note;
        this.liquidationPhotos = List.copyOf(photos);
        this.surplusAmount = surplus;
    }

    public void markSurplusRefunded(Long fmId, Instant now) {
        if (surplusAmount == null || surplusAmount.signum() <= 0 || surplusRefundedAt != null) {
            throw new IllegalStateException("ReclaimedGoods " + id + " không có tiền dư chờ hoàn");
        }
        this.surplusRefundedBy = fmId;
        this.surplusRefundedAt = now;
    }

    private void requireHolding() {
        if (!holding()) {
            throw new IllegalStateException("ReclaimedGoods " + id + " không còn lưu giữ: " + status);
        }
    }
}
