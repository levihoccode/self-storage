package vn.lemar.selfstorage.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hóa đơn. Nguồn số tiền tin cậy khi thu tiền — cổng thanh toán đối chiếu `vnp_Amount` với
 * {@code amount} ở đây chứ không tin tham số caller truyền vào.
 *
 * <p>Chỉ map những cột Flow 1.4 dùng tới; cột khác để module sở hữu hóa đơn bổ sung khi cần.
 * {@code orderId}/{@code customerId} để kiểu {@code Long} cho nhất quán với cách
 * {@code PaymentTransaction} tham chiếu hóa đơn.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 20)
    private String type;

    @Convert(converter = InvoiceStatusConverter.class)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "due_date")
    private Instant dueDate;

    protected Invoice() {
        // JPA
    }

    public boolean isUnpaid() {
        return status == InvoiceStatus.UNPAID;
    }

    public boolean belongsTo(Long accountId) {
        return customerId != null && customerId.equals(accountId);
    }

    /** Hóa đơn hết hiệu lực vì khoang không còn dùng được — Flow 1.4 nhánh Reserved / Maintenance. */
    public void cancel() {
        this.status = InvoiceStatus.CANCELED;
    }

    /** Chỉ gọi từ listener khi cổng đã xác nhận thu tiền. */
    public void markPaid() {
        this.status = InvoiceStatus.PAID;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getDueDate() {
        return dueDate;
    }
}
