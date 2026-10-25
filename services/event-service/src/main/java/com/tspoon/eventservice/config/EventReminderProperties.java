package com.tspoon.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param enabled  리마인더 스케줄러를 돌릴지 (테스트에서는 끄고 직접 부른다)
 * @param leadTime 시작까지 이만큼 남은 행사의 신청자에게 보낸다
 * @param interval 보낼 행사를 찾는 주기
 */
@ConfigurationProperties("tspoon.event.reminder")
public record EventReminderProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("24h") Duration leadTime,
        @DefaultValue("30s") Duration interval
) {
}
