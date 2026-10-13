package vn.lemar.selfstorage.booking.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Đọc policy số do BOM đặt (bảng {@code policies}).
 *
 * <p>Tạm đặt trong booking vì chưa module nào nhận sở hữu bảng này; khi có module policy dùng
 * chung thì chuyển sang đó và xoá class này.
 */
@Repository
public class PolicyReader {

    private final JdbcTemplate jdbc;

    public PolicyReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Giá trị nguyên của policy; trả {@code fallback} khi thiếu key hoặc giá trị không phải số. */
    public int intValue(String key, int fallback) {
        return jdbc.queryForList("select value from policies where key = ?", String.class, key)
                .stream()
                .findFirst()
                .map(raw -> parseOr(raw, fallback))
                .orElse(fallback);
    }

    private int parseOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
