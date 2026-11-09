package vn.lemar.selfstorage.payment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import vn.lemar.selfstorage.payment.application.RefundReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Nghĩa vụ hoàn tiền được tạo trước khi chi tiền. {@code amount} là final amount do server
 * tính từ các dòng; {@code calculationInput} là snapshot input/policy để giải thích lại.
 */
@Entity
@Table(name = "refund_obligations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundObligation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30)
    private RefundReason reasonCode;

    @Column(name = "reason_note")
    private String reasonNote;

    @Convert(converter = RefundObligationStatusConverter.class)
    @Column(nullable = false, length = 20)
    private RefundObligationStatus status = RefundObligationStatus.OPEN;

    @Column(name = "calculation_version", nullable = false, length = 50)
    private String calculationVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_input", columnDefinition = "jsonb")
    private JsonNode calculationInput;

    @Column(name = "formula_text", nullable = false, columnDefinition = "text")
    private String formulaText;

    @Column(name = "created_by_account_id")
    private Long createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "refundObligation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<RefundObligationLine> lines = new ArrayList<>();

    public static RefundObligation create(Long invoiceId, Long orderId, RefundReason reasonCode,
            String reasonNote, String calculationVersion, JsonNode calculationInput,
            Long createdByAccountId) {
        RefundObligation obligation = new RefundObligation();
        obligation.invoiceId = invoiceId;
        obligation.orderId = orderId;
        obligation.reasonCode = reasonCode;
        obligation.reasonNote = reasonNote;
        obligation.calculationVersion = calculationVersion;
        obligation.calculationInput = calculationInput;
        obligation.createdByAccountId = createdByAccountId;
        return obligation;
    }

    public void addLine(String componentCode, RefundLineDirection direction, BigDecimal amount,
            String label, String sourceType, Long sourceId, String note, int sortOrder) {
        lines.add(RefundObligationLine.create(this, componentCode, direction, amount, label,
                sourceType, sourceId, note, sortOrder));
    }

    /** Tính final amount và formula text; gọi một lần sau khi thêm đủ lines. */
    public void calculate() {
        BigDecimal total = BigDecimal.ZERO;
        StringBuilder formula = new StringBuilder();
        for (RefundObligationLine line : lines) {
            if (line.getDirection() == RefundLineDirection.ADD) {
                total = total.add(line.getAmount());
                if (formula.length() > 0) {
                    formula.append(" + ");
                }
            } else {
                total = total.subtract(line.getAmount());
                formula.append(formula.length() == 0 ? "-" : " - ");
            }
            formula.append(line.getAmount().toPlainString());
        }
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("Final refund amount phải lớn hơn 0");
        }
        this.amount = total;
        this.formulaText = formula.append(" = ").append(total.toPlainString()).toString();
        this.updatedAt = Instant.now();
    }
}
