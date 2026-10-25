package vn.lemar.selfstorage.booking.application.exception;

public class StorageUnitUnavailableException extends RuntimeException {

    public StorageUnitUnavailableException() {
        super("Khoang đã không còn khả dụng, vui lòng chọn khoang khác");
    }
}
