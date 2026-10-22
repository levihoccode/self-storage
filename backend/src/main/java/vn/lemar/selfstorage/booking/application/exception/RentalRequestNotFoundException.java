package vn.lemar.selfstorage.booking.application.exception;

public class RentalRequestNotFoundException extends RuntimeException {

    public RentalRequestNotFoundException() {
        super("Không tìm thấy yêu cầu đặt kho");
    }
}
