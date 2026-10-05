package vn.lemar.selfstorage.notification.application.dto;

import java.time.Instant;

import vn.lemar.selfstorage.notification.domain.Notification;

/** Summary cho danh sách — không trả {@code body}. */
public record NotificationSummaryResponse(
        Long id,
        String type,
        String title,
        Instant readAt,
        Instant createdAt) {

    public static NotificationSummaryResponse from(Notification notification) {
        return new NotificationSummaryResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
