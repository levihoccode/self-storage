package vn.lemar.selfstorage.notification.application.dto;

import java.time.Instant;

import vn.lemar.selfstorage.notification.domain.Notification;

/** Summary cho danh sách — không trả {@code body}; {@code orderId} null nếu không gắn đơn. */
public record NotificationSummaryResponse(
        Long id,
        String type,
        String title,
        Instant readAt,
        Instant createdAt,
        Long orderId) {

    public static NotificationSummaryResponse from(Notification notification, Long orderId) {
        return new NotificationSummaryResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                orderId);
    }
}
