package vn.lemar.selfstorage.booking.application.exception;

public class RentalRequestAlreadyHandledException extends RuntimeException {

    public RentalRequestAlreadyHandledException() {
        super("Yêu cầu đã được xử lý bởi FM khác");
    }
}
