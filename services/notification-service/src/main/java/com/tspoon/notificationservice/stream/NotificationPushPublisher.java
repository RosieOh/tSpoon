package com.tspoon.notificationservice.stream;

import com.tspoon.notificationservice.entity.Notification.NotificationChannel;
import com.tspoon.notificationservice.entity.Notification.NotificationStatus;
import com.tspoon.notificationservice.repository.NotificationRepository;
import com.tspoon.notificationservice.service.NotificationService.InAppNotificationCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * 앱 내 알림이 커밋되면 Redis 채널로 알린다. 그 회원이 어느 인스턴스에 연결돼 있든 받을 수 있다.
 * 실시간 전달은 덤이다 — Redis 가 잠깐 안 돼도 알림은 이미 저장됐고 목록 API 로 볼 수 있으므로 실패는 기록만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushPublisher {

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final NotificationRepository notificationRepository;
    private final NotificationStreamProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(InAppNotificationCreated created) {
        try {
            long unread = notificationRepository.countByUserIdAndChannelAndStatus(
                    created.userId(), NotificationChannel.IN_APP, NotificationStatus.UNREAD);
            redis.convertAndSend(properties.channel(),
                    jsonMapper.writeValueAsString(new NotificationPush(created.userId(), created.notification(), unread)));
        } catch (RuntimeException e) {
            log.warn("알림 {} 을 실시간으로 전하지 못했습니다: {}", created.notification().id(), e.getMessage());
        }
    }
}
