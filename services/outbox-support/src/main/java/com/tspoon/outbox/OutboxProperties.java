package com.tspoon.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param relayEnabled 릴레이(Kafka 전송)를 돌릴지. 이벤트 기록은 항상 한다
 * @param batchSize    한 주기에 보낼 최대 건수
 * @param sendTimeout  Kafka 전송 확인을 기다리는 시간
 */
@ConfigurationProperties("tspoon.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean relayEnabled,
        @DefaultValue("100") int batchSize,
        @DefaultValue("5s") Duration sendTimeout
) {
}
