package com.tspoon.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param maxFailures  잠그기까지 허용하는 연속 실패 횟수
 * @param window       실패 횟수를 세는 기간 (첫 실패부터)
 * @param lockDuration 잠그는 시간 (기준 횟수에 닿은 실패부터)
 */
@ConfigurationProperties("tspoon.user.login-lock")
public record LoginLockProperties(
        @DefaultValue("5") int maxFailures,
        @DefaultValue("15m") Duration window,
        @DefaultValue("15m") Duration lockDuration
) {
}
