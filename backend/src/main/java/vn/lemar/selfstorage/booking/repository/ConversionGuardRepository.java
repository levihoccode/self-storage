package vn.lemar.selfstorage.booking.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Chặn convert trùng cho cùng cặp (account, {@code unit_id}) — spec 1.2 Case A: không tạo hai
 * {@code RentalOrder}/proposal đang hoạt động cho cùng một account và cùng một khoang.
 */
@Repository
public class ConversionGuardRepository {

    private static final String LOCK_NAMESPACE = "booking.conversion:";

    private final JdbcTemplate jdbc;

    public ConversionGuardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Khoá advisory theo account tới hết transaction hiện tại. Hai luồng convert (duyệt + claim sau
     * xác minh) của cùng account chạy tuần tự, nên bước "kiểm trùng rồi tạo" không bị race. Các
     * request khác nhau trỏ cùng khoang nằm ở các row khác nhau — khoá row request không đủ.
     */
    public void lockAccount(Long accountId) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> null, LOCK_NAMESPACE + accountId);
    }

    /**
     * Account đã có proposal {@code Pending}/{@code Agreed} cho khoang này trên một đơn chưa đóng
     * ({@code Canceled}/{@code Done}/{@code Expired}).
     */
    public boolean hasActiveConversion(Long accountId, Long unitId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from proposal_feedbacks p
                    join rental_orders o on o.id = p.order_id
                    where o.customer_id = ? and p.unit_id = ?
                      and p.status in ('Pending', 'Agreed')
                      and o.status not in ('Canceled', 'Done', 'Expired'))""",
                Boolean.class, accountId, unitId));
    }
}
