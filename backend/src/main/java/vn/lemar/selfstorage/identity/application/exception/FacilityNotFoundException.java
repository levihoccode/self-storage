package vn.lemar.selfstorage.identity.application.exception;

public class FacilityNotFoundException extends RuntimeException {

    public FacilityNotFoundException() {
        super("Không tìm thấy cơ sở");
    }
}
