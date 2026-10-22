package vn.lemar.selfstorage.facility.application;

/**
 * Ảnh chụp một khoang cho module khác — không lộ entity {@code domain} ra ngoài module.
 *
 * @param status giá trị PascalCase như trong DB ({@code Available}, {@code Reserved}, ...)
 */
public record StorageUnitView(Long id, String code, Long facilityId, Long unitTypeId, String status) {

    private static final String AVAILABLE = "Available";

    /** Spec: chỉ khoang {@code Available} mới được chỉ định hoặc đặt cọc. */
    public boolean isAvailable() {
        return AVAILABLE.equals(status);
    }
}
