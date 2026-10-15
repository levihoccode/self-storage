package vn.lemar.selfstorage.booking.application.exception;

public class UnitTypeNotFoundException extends RuntimeException {

    public UnitTypeNotFoundException() {
        super("Không tìm thấy loại khoang");
    }
}
