package vn.lemar.selfstorage.booking.application.exception;

/** 403 — người gọi không phải khách sở hữu proposal. */
public class ProposalForbiddenException extends RuntimeException {

    public ProposalForbiddenException() {
        super("Bạn không phải chủ của đơn thuê này");
    }
}
