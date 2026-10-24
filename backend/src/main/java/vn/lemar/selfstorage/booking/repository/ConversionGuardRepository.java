package vn.lemar.selfstorage.booking.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.lemar.selfstorage.booking.domain.RentalOrderStatus;

/**
 * Chặn convert trùng cho cùng cặp (account, {@code unit_id}) — spec 1.2 Case A: không tạo hai
 * {@code RentalOrder}/proposal đang hoạt động cho cùng một account và cùng một khoang.
 */
@Repository
public class ConversionGuardRepository {

    private static final String LOCK_NAMESPACE = "booking.conversion:";
    /** Sinh từ enum để enum đổi định nghĩa "đơn đóng" thì query theo luôn. */
    private static final List<String> CLOSED_STATUSES = java.util.Arrays.stream(RentalOrderStatus.values())
            .filter(RentalOrderStatus::isClosed)
            .map(RentalOrderStatus::dbValue)
            .toList();
    private static final String CLOSED_PLACEHOLDERS = String.join(", ",
            CLOSED_STATUSES.stream().map(status -> "?").toList());

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
     * <p>Danh sách status đóng sinh từ {@code RentalOrderStatus#isClosed()}, không hardcode.
     */
    public boolean hasActiveConversion(Long accountId, Long unitId) {
        List<Object> args = new ArrayList<>();
        args.add(accountId);
        args.add(unitId);
        args.addAll(CLOSED_STATUSES);
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from proposal_feedbacks p
                    join rental_orders o on o.id = p.order_id
                    where o.customer_id = ? and p.unit_id = ?
                      and p.status in ('Pending', 'Agreed')
                      and o.status not in (%s))""".formatted(CLOSED_PLACEHOLDERS),
                Boolean.class, args.toArray()));
    }

    static List<String> closedStatuses() {
        return CLOSED_STATUSES;
    }

    /**
     * Email khách đã có đơn/proposal đang hoạt động cho khoang này — dùng cho pre-check lúc duyệt.
     * Khác {@link #hasActiveConversion(Long, Long)} ở chỗ chưa cần account id (email có thể đang
     * pending đăng ký); đơn cũ đã đóng thì không chặn, cho gán lại khoang.
     */
    public boolean hasActiveConversionForEmail(String normalizedEmail, Long unitId) {
        List<Object> args = new ArrayList<>();
        args.add(normalizedEmail.trim().toLowerCase(Locale.ROOT));
        args.add(unitId);
        args.addAll(CLOSED_STATUSES);
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from proposal_feedbacks p
                    join rental_orders o on o.id = p.order_id
                    join accounts a on a.id = o.customer_id
                    where a.email = ? and p.unit_id = ?
                      and p.status in ('Pending', 'Agreed')
                      and o.status not in (%s))""".formatted(CLOSED_PLACEHOLDERS),
                Boolean.class, args.toArray()));
    }
}
