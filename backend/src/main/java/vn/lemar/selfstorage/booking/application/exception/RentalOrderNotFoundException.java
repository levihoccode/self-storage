package vn.lemar.selfstorage.booking.application.exception;

/** 404 — đơn thuê không tồn tại. */
public class RentalOrderNotFoundException extends RuntimeException {

    public RentalOrderNotFoundException() {
        super("Không tìm thấy đơn thuê");
    }
}
