package vn.lemar.selfstorage.facility.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FacilityAccessRepository {

    private final JdbcTemplate jdbc;

    public FacilityAccessRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isManagedBy(Long accountId, Long facilityId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from facilities "
                        + "where id = ? and fm_account_id = ?)",
                Boolean.class, facilityId, accountId));
    }

    /** FM đang phụ trách cơ sở; rỗng khi cơ sở chưa gán FM hoặc không tồn tại. */
    public Optional<Long> findManagerAccountId(Long facilityId) {
        return jdbc.queryForList(
                        "select fm_account_id from facilities where id = ? and fm_account_id is not null",
                        Long.class, facilityId)
                .stream().findFirst();
    }

    public boolean exists(Long facilityId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from facilities where id = ?)",
                Boolean.class, facilityId));
    }
}