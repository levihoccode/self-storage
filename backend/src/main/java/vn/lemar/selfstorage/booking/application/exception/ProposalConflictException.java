package vn.lemar.selfstorage.booking.application.exception;

/** 409 — trạng thái hiện tại không cho phép thao tác (proposal hết Pending, khoang hết Available, đơn đã đóng). */
public class ProposalConflictException extends RuntimeException {

    public ProposalConflictException(String message) {
        super(message);
    }
}
