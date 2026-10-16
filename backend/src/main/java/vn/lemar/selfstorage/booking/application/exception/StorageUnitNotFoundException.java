package vn.lemar.selfstorage.booking.application.exception;

/** 404 — khoang không tồn tại hoặc không thuộc cơ sở của đơn. */
public class StorageUnitNotFoundException extends RuntimeException {

    public StorageUnitNotFoundException() {
        super("Không tìm thấy khoang tại cơ sở của đơn");
    }
}
