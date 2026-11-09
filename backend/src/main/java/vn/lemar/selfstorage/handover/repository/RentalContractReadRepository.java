package vn.lemar.selfstorage.handover.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Hợp đồng đang hiệu lực của một khách, join sẵn khoang + cơ sở + loại khoang cho màn "Kho của tôi".
 * Đọc thuần, không ghi.
 */
@Repository
public class RentalContractReadRepository {

    private static final String ACTIVE_CONTRACTS_SQL = """
            select c.id, c.code, c.status, c.order_id, c.unit_id,
                   u.code as unit_code, u.size as unit_size, u.facility_id,
                   f.name as facility_name, f.address as facility_address,
                   t.name as unit_type_name,
                   c.monthly_price, c.deposit_amount, c.period,
                   c.start_date, c.end_date, c.signed_at
            from rental_contracts c
            join storage_units u on u.id = c.unit_id
            join facilities f on f.id = u.facility_id
            join unit_types t on t.id = u.unit_type_id
            where c.customer_id = ? and c.status = 'Active'
            order by c.end_date asc nulls last, c.id asc
            """;

    private static final RowMapper<ContractRow> ROW_MAPPER = (rs, rowNum) -> new ContractRow(
            rs.getLong("id"), rs.getString("code"), rs.getString("status"), rs.getLong("order_id"),
            rs.getLong("unit_id"), rs.getString("unit_code"), rs.getString("unit_size"),
            rs.getLong("facility_id"), rs.getString("facility_name"), rs.getString("facility_address"),
            rs.getString("unit_type_name"), rs.getBigDecimal("monthly_price"),
            rs.getBigDecimal("deposit_amount"), rs.getInt("period"),
            rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
            rs.getTimestamp("signed_at") == null ? null : rs.getTimestamp("signed_at").toInstant());

    private final JdbcTemplate jdbc;

    public RentalContractReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Hợp đồng {@code Active} của khách, hạn gần nhất trước. */
    public List<ContractRow> findActiveByCustomer(Long customerId) {
        return jdbc.query(ACTIVE_CONTRACTS_SQL, ROW_MAPPER, customerId);
    }

    /** Một dòng hợp đồng đã join khoang/cơ sở/loại khoang. */
    public record ContractRow(Long id, String code, String status, Long orderId, Long unitId,
                              String unitCode, String unitSize, Long facilityId, String facilityName,
                              String facilityAddress, String unitTypeName, BigDecimal monthlyPrice,
                              BigDecimal depositAmount, Integer period, LocalDate startDate,
                              LocalDate endDate, Instant signedAt) {
    }
}
