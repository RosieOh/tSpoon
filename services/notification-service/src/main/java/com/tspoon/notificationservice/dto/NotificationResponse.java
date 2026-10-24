package com.tspoon.notificationservice.dto;

import com.tspoon.notificationservice.entity.Notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String title,
        String content,
        Notification.NotificationType type,
        Notification.NotificationChannel channel,
        Notification.NotificationStatus status,
        boolean read,
        String actionUrl,
        String relatedEntityType,
        Long relatedEntityId,
        Instant createdAt,
        Instant sentAt,
        Instant readAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getTitle(), notification.getContent(), notification.getType(),
                notification.getChannel(), notification.getStatus(),
                notification.getStatus() == Notification.NotificationStatus.READ,
                notification.getActionUrl(), notification.getRelatedEntityType(), notification.getRelatedEntityId(),
                notification.getCreatedAt(), notification.getSentAt(), notification.getReadAt());
    }
}
