package vn.lemar.selfstorage.notification.application.dto;

import java.time.Instant;

import vn.lemar.selfstorage.notification.domain.Notification;

/** Chi tiết một notification của account hiện tại. */
public record NotificationDetailResponse(
        Long id,
        String type,
        String title,
        String body,
        Instant readAt,
        Instant createdAt) {

    public static NotificationDetailResponse from(Notification notification) {
        return new NotificationDetailResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
