package vn.lemar.selfstorage.media.application.exception;

/** File không hợp lệ (rỗng, sai loại, quá kích thước) — lỗi người dùng, trả 400. */
public class InvalidUploadException extends RuntimeException {

    public InvalidUploadException(String message) {
        super(message);
    }
}
