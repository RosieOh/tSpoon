package com.tspoon.notificationservice.stream;

import com.tspoon.notificationservice.dto.NotificationResponse;

/**
 * 인스턴스 사이에 Redis pub/sub 으로 주고받는 새 알림.
 *
 * @param unreadCount 알림을 만든 직후의 안 읽은 알림 수
 */
public record NotificationPush(Long userId, NotificationResponse notification, long unreadCount) {
}
