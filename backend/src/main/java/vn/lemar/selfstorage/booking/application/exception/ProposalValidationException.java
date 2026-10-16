package vn.lemar.selfstorage.booking.application.exception;

/** 400 — thiếu dữ liệu bắt buộc theo spec (lý do từ chối, lý do override). */
public class ProposalValidationException extends RuntimeException {

    public ProposalValidationException(String message) {
        super(message);
    }
}
