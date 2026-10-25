-- V4__facility_unit_type.sql - FACILITY_UNIT_TYPE (B1, spec draft.md:125).
-- Mỗi facility cung cấp 1+ loại unit_type. Cặp (facility_id, unit_type_id) duy nhất
-- nhờ PRIMARY KEY. Deviate so với db-table-draft.md:130 (chờ Flow 4/5) — owner chốt ở #126.
CREATE TABLE facility_unit_type (
    facility_id  BIGINT NOT NULL REFERENCES facilities (id) ON DELETE CASCADE,
    unit_type_id BIGINT NOT NULL REFERENCES unit_types (id) ON DELETE CASCADE,
    PRIMARY KEY (facility_id, unit_type_id)
);
CREATE INDEX idx_facility_unit_type_facility ON facility_unit_type (facility_id);
CREATE INDEX idx_facility_unit_type_unit_type ON facility_unit_type (unit_type_id);

-- Seed khác nhau giữa các cơ sở (#126): Q7 đủ 3 loại, TD không cung cấp Large.
INSERT INTO facility_unit_type (facility_id, unit_type_id)
SELECT f.id, u.id FROM facilities f CROSS JOIN unit_types u WHERE f.code = 'Q7';
INSERT INTO facility_unit_type (facility_id, unit_type_id)
SELECT f.id, u.id FROM facilities f CROSS JOIN unit_types u
WHERE f.code = 'TD' AND u.name IN ('Small', 'Medium');
