package com.tspoon.notificationservice.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param timeout   연결 하나를 유지하는 최대 시간. 지나면 닫고 클라이언트가 다시 연결한다
 * @param heartbeat 프록시·로드밸런서가 유휴 연결을 끊지 않도록 주석 한 줄을 보내는 주기
 * @param channel   인스턴스 사이에 새 알림을 퍼뜨리는 Redis 채널
 */
@ConfigurationProperties("tspoon.notification.stream")
public record NotificationStreamProperties(
        @DefaultValue("30m") Duration timeout,
        @DefaultValue("25s") Duration heartbeat,
        @DefaultValue("tspoon:notifications:created") String channel
) {
}
