package vn.lemar.selfstorage.facility.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API đọc trạng thái khoang cho module khác (B1 re-check Available khi duyệt).
 */
@Service
public class StorageUnitQueryService {

    private final JdbcTemplate jdbc;

    public StorageUnitQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(Long unitId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from storage_units where id = ? and status = 'Available')",
                Boolean.class, unitId));
    }
}
