package vn.lemar.selfstorage.pricing.application;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API đọc policy key-value (owner: Flow 4; các flow chỉ đọc qua key).
 */
@Service
public class PolicyQueryService {

    private final JdbcTemplate jdbc;

    public PolicyQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<String> valueOf(String key) {
        return Optional.ofNullable(jdbc.query(
                "select value from policies where key = ?", (rs, row) -> rs.getString(1), key)
                .stream().findFirst().orElse(null));
    }
}
