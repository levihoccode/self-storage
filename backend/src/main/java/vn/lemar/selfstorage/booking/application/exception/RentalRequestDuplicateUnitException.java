package vn.lemar.selfstorage.booking.application.exception;

public class RentalRequestDuplicateUnitException extends RuntimeException {

    public RentalRequestDuplicateUnitException() {
        super("Khoang đã được gán cho yêu cầu khác của cùng khách hàng, "
                + "vui lòng chọn khoang khác");
    }
}
