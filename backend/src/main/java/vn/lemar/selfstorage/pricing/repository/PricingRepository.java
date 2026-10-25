package vn.lemar.selfstorage.pricing.repository;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc policy và giá thuê hiệu lực — chưa có entity vì Flow 4 (quản lý giá/chính sách) chưa làm. */
@Repository
public class PricingRepository {

    private final JdbcTemplate jdbc;

    public PricingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Giá trị policy thô theo key; rỗng nếu key chưa seed. */
    public Optional<String> findPolicyValue(String key) {
        return jdbc.query("select value from policies where key = ?", (rs, rowNum) -> rs.getString(1), key)
                .stream().findFirst();
    }

    /** {@code StorageUnit.monthly_price} nếu có (override BOM duyệt), mặc định {@code UnitType.monthly_price}. */
    public Optional<BigDecimal> findEffectiveMonthlyPrice(Long unitId) {
        return jdbc.query("select coalesce(u.monthly_price, t.monthly_price) from storage_units u "
                        + "join unit_types t on t.id = u.unit_type_id where u.id = ?",
                (rs, rowNum) -> rs.getBigDecimal(1), unitId).stream().findFirst();
    }
}
