package vn.lemar.selfstorage.booking.repository;

import java.util.Locale;
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
     * Khoá advisory theo email khách (đã chuẩn hoá) tới hết transaction hiện tại. Duyệt và claim
     * sau xác minh của cùng email chạy tuần tự, nên bước "kiểm trùng rồi tạo" không bị race.
     *
     * <p>Khoá theo email chứ không theo account id: lúc FM duyệt, account có thể chưa hiện ra (đang
     * xác minh, chưa commit) — khoá theo account thì nhánh "chưa có account xác minh" không có gì để
     * khoá, và claim chạy song song sẽ bỏ sót request vừa duyệt. Email unique theo account nên
     * claim↔claim vẫn tuần tự. Khoá row request không đủ: các request khác nhau là các row khác nhau.
     */
    public void lockCustomerEmail(String normalizedEmail) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> null, LOCK_NAMESPACE + normalizedEmail.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Account đã có proposal {@code Pending}/{@code Agreed} cho khoang này trên một đơn chưa đóng.
     *
     * <p>Danh sách status đóng chép lại {@code RentalOrderStatus#isClosed()} (Canceled/Done)
     * — đổi định nghĩa đơn "đóng" thì phải sửa cả hai nơi.
     */
    public boolean hasActiveConversion(Long accountId, Long unitId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from proposal_feedbacks p
                    join rental_orders o on o.id = p.order_id
                    where o.customer_id = ? and p.unit_id = ?
                      and p.status in ('Pending', 'Agreed')
                      and o.status not in ('Canceled', 'Done'))""",
                Boolean.class, accountId, unitId));
    }
}
