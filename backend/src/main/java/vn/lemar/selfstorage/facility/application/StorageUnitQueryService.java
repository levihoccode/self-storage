package vn.lemar.selfstorage.facility.application;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API đọc trạng thái khoang cho module khác (B1 kiểm tra khoang khi duyệt).
 */
@Service
public class StorageUnitQueryService {

    private final JdbcTemplate jdbc;

    public StorageUnitQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Trạng thái + cơ sở của một khoang. Khoang phải thuộc đúng cơ sở của request
     * (spec draft.md §1.1: kho còn sẵn "tại chi nhánh") và đang {@code Available}.
     */
    @Transactional(readOnly = true)
    public Optional<UnitInfo> findUnitInfo(Long unitId) {
        return jdbc.query(
                "select facility_id, status from storage_units where id = ?",
                (rs, row) -> new UnitInfo(rs.getLong(1), rs.getString(2)), unitId)
                .stream().findFirst();
    }

    /** Một dòng thông tin khoang cho caller kiểm tra trước khi duyệt. */
    public record UnitInfo(Long facilityId, String status) {

        public boolean available() {
            return "Available".equals(status);
        }
    }
}
