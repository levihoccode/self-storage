package vn.lemar.selfstorage.facility.repository;

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

    public boolean exists(Long facilityId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from facilities where id = ?)",
                Boolean.class, facilityId));
    }
}