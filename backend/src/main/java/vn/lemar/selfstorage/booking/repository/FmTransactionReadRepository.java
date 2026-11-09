package vn.lemar.selfstorage.booking.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read model lịch sử giao dịch của cơ sở — union {@code invoices} + {@code payment_transactions}
 * + {@code refund_transactions}. Không ghi, không migration; mỗi bảng giữ vai trò riêng nên một
 * khoản tiền có thể xuất hiện hai dòng khác {@code kind} (invoice phát hành và payment thu tiền).
 */
@Repository
public class FmTransactionReadRepository {

    /**
     * Invoice thuộc cơ sở qua khoang của đơn hoặc hợp đồng. Inner join cơ sở giữ đúng hành vi read
     * model hoá đơn FM: invoice không gắn được khoang/cơ sở không thuộc lịch sử của cơ sở nào.
     */
    private static final String BASE = """
            with base as (
                select i.id as invoice_id, i.code as invoice_code, i.title, i.type as invoice_type,
                       i.status as invoice_status, i.amount as invoice_amount, i.created_at as invoice_created_at,
                       i.refund_status as invoice_refund_status, i.refund_reason as invoice_refund_reason,
                       i.order_id, i.customer_id, coalesce(r.customer_name, a.email) as customer_name,
                       a.email as customer_email, f.id as facility_id, f.name as facility_name,
                       u.code as unit_code
                from invoices i
                left join rental_orders o on o.id = i.order_id
                left join rental_contracts c on c.id = i.contract_id
                left join storage_units u on u.id = coalesce(o.unit_id, c.unit_id)
                join facilities f on f.id = u.facility_id
                join accounts a on a.id = i.customer_id
                left join rental_requests r on r.id = o.request_id
                where f.id = ?
            )
            """;

    /** Mỗi nhánh trả cùng bộ cột; nhánh không có cột thì cast null để Postgres suy kiểu. */
    private static final String UNION = """
            select case when i.invoice_type in ('Penalty', 'Service', 'Extension')
                        then 'FEE' else 'INVOICE' end as kind,
                   'IN' as direction,
                   i.invoice_id as source_id,
                   i.invoice_amount as amount,
                   i.invoice_status as status,
                   i.invoice_created_at as occurred_at,
                   i.order_id,
                   i.invoice_id,
                   i.invoice_code,
                   cast(null as bigint) as payment_transaction_id,
                   cast(null as bigint) as refund_transaction_id,
                   i.invoice_code as code,
                   cast(null as varchar) as method,
                   i.title as description,
                   i.customer_id,
                   i.customer_name,
                   i.customer_email,
                   i.facility_id,
                   i.facility_name,
                   i.unit_code,
                   i.invoice_refund_status as invoice_refund_status,
                   i.invoice_refund_reason as invoice_refund_reason,
                   cast(null as timestamptz) as refund_confirmed_at,
                   cast(null as timestamptz) as refund_disputed_at,
                   cast(null as text) as refund_dispute_note
            from base i
            union all
            select 'PAYMENT',
                   'IN',
                   pt.id,
                   pt.amount,
                   pt.status,
                   coalesce(pt.paid_at, pt.created_at),
                   i.order_id,
                   i.invoice_id,
                   i.invoice_code,
                   pt.id,
                   cast(null as bigint),
                   pt.vnp_txn_ref,
                   'VNPAY',
                   'Thanh toán ' || i.invoice_code,
                   i.customer_id,
                   i.customer_name,
                   i.customer_email,
                   i.facility_id,
                   i.facility_name,
                   i.unit_code,
                   cast(null as varchar),
                   cast(null as varchar),
                   cast(null as timestamptz),
                   cast(null as timestamptz),
                   cast(null as text)
            from payment_transactions pt
            join base i on i.invoice_id = pt.invoice_id
            where pt.direction = 'PAY'
            union all
            select 'REFUND',
                   'OUT',
                   rt.id,
                   rt.amount,
                   rt.status,
                   coalesce(rt.disbursed_at, rt.created_at),
                   i.order_id,
                   i.invoice_id,
                   i.invoice_code,
                   cast(null as bigint),
                   rt.id,
                   coalesce(rt.gateway_ref, 'REFUND-' || rt.id),
                   rt.method,
                   'Hoàn tiền ' || i.invoice_code,
                   i.customer_id,
                   i.customer_name,
                   i.customer_email,
                   i.facility_id,
                   i.facility_name,
                   i.unit_code,
                   cast(null as varchar),
                   cast(null as varchar),
                   rt.customer_confirmed_at,
                   rt.disputed_at,
                   rt.dispute_note
            from refund_transactions rt
            join base i on i.invoice_id = rt.invoice_id
            """;

    private static final String FILTER = """
            where (cast(? as text) is null or tx.kind = cast(? as text))
              and (cast(? as text) is null or tx.direction = cast(? as text))
              and (cast(? as text) is null or tx.status = cast(? as text))
              and (cast(? as text) is null or tx.invoice_refund_status = cast(? as text))
              and (cast(? as timestamptz) is null or (tx.kind = 'PAYMENT' and tx.status = 'Pending'
                   and tx.occurred_at <= cast(? as timestamptz)))
              and (cast(? as timestamptz) is null or tx.occurred_at >= cast(? as timestamptz))
              and (cast(? as timestamptz) is null or tx.occurred_at < cast(? as timestamptz))
            """;

    private final JdbcTemplate jdbc;

    public FmTransactionReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TransactionRow> findPage(Long facilityId, String kind, String direction, String status,
            String refundStatus, Instant pendingCutoff, Instant from, Instant to, long offset,
            int limit) {
        String sql = BASE + "select * from (" + UNION + ") tx " + FILTER
                + "order by tx.occurred_at desc, tx.source_id desc, tx.kind limit ? offset ?";
        return jdbc.query(sql, (rs, rowNum) -> new TransactionRow(
                        rs.getLong("source_id"), rs.getString("kind"), rs.getString("direction"),
                        rs.getBigDecimal("amount"), rs.getString("status"),
                        instant(rs, "occurred_at"),
                        rs.getObject("order_id", Long.class), rs.getObject("invoice_id", Long.class),
                        rs.getString("invoice_code"),
                        rs.getObject("payment_transaction_id", Long.class),
                        rs.getObject("refund_transaction_id", Long.class),
                        rs.getString("code"), rs.getString("method"), rs.getString("description"),
                        rs.getLong("customer_id"), rs.getString("customer_name"),
                        rs.getString("customer_email"), rs.getLong("facility_id"),
                        rs.getString("facility_name"), rs.getString("unit_code"),
                        rs.getString("invoice_refund_status"), rs.getString("invoice_refund_reason"),
                        instant(rs, "refund_confirmed_at"), instant(rs, "refund_disputed_at"),
                        rs.getString("refund_dispute_note")),
                facilityId, kind, kind, direction, direction, status, status,
                refundStatus, refundStatus, timestamp(pendingCutoff), timestamp(pendingCutoff),
                timestamp(from), timestamp(from), timestamp(to), timestamp(to), limit, offset);
    }

    public long count(Long facilityId, String kind, String direction, String status,
            String refundStatus, Instant pendingCutoff, Instant from, Instant to) {
        String sql = BASE + "select count(*) from (" + UNION + ") tx " + FILTER;
        Long count = jdbc.queryForObject(sql, Long.class,
                facilityId, kind, kind, direction, direction, status, status,
                refundStatus, refundStatus, timestamp(pendingCutoff), timestamp(pendingCutoff),
                timestamp(from), timestamp(from), timestamp(to), timestamp(to));
        return count == null ? 0 : count;
    }

    private static java.sql.Timestamp timestamp(Instant instant) {
        return instant == null ? null : java.sql.Timestamp.from(instant);
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record TransactionRow(Long id, String kind, String direction, BigDecimal amount, String status,
            Instant occurredAt, Long orderId, Long invoiceId, String invoiceCode,
            Long paymentTransactionId, Long refundTransactionId, String code, String method,
            String description, Long customerId, String customerName, String customerEmail,
            Long facilityId, String facilityName, String unitCode, String invoiceRefundStatus,
            String invoiceRefundReason, Instant refundConfirmedAt, Instant refundDisputedAt,
            String refundDisputeNote) {
    }
}
