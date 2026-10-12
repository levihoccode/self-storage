package vn.lemar.selfstorage.booking.application.exception;

/** 404 — proposal không tồn tại. */
public class ProposalNotFoundException extends RuntimeException {

    public ProposalNotFoundException() {
        super("Không tìm thấy đề xuất khoang");
    }
}
