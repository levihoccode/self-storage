package vn.lemar.selfstorage.notification.application.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("Không tìm thấy thông báo");
    }
}
