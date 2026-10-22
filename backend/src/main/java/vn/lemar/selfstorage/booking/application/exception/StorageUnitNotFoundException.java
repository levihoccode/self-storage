package vn.lemar.selfstorage.booking.application.exception;

/**
 * Khoang FM chỉ định không tồn tại hoặc không thuộc cơ sở của request — 404.
 * Thuộc cơ sở khác cũng trả về cùng lỗi để không lộ tài nguyên ngoài phạm vi FM.
 */
public class StorageUnitNotFoundException extends RuntimeException {

    public StorageUnitNotFoundException() {
        super("Không tìm thấy khoang");
    }
}
