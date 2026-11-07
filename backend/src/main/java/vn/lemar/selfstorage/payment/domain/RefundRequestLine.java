package vn.lemar.selfstorage.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một dòng cấu thành final amount của refund request. */
@Entity
@Table(name = "refund_request_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRequestLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_request_id", nullable = false)
    private RefundRequest refundRequest;

    @Column(name = "component_code", nullable = false, length = 40)
    private String componentCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RefundLineDirection direction;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    static RefundRequestLine create(RefundRequest request, String componentCode,
            RefundLineDirection direction, BigDecimal amount, String label, String sourceType,
            Long sourceId, String note, int sortOrder) {
        RefundRequestLine line = new RefundRequestLine();
        line.refundRequest = request;
        line.componentCode = componentCode;
        line.direction = direction;
        line.amount = amount;
        line.label = label;
        line.sourceType = sourceType;
        line.sourceId = sourceId;
        line.note = note;
        line.sortOrder = sortOrder;
        return line;
    }
}
