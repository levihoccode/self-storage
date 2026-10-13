package vn.lemar.selfstorage.pricing.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc bảng {@code policies} (key-value, Flow 4) — chỉ đọc, chưa có UI sửa ở MVP. */
@Repository
public class PolicyRepository {

    private final JdbcTemplate jdbc;

    public PolicyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findValue(String key) {
        return jdbc.query("select value from policies where key = ?", (rs, rowNum) -> rs.getString(1), key)
                .stream().findFirst();
    }
}
