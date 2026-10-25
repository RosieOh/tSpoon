package com.tspoon.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param tokenTtl       재설정 링크 유효 시간
 * @param requestCooldown 재설정 메일을 다시 보낼 수 있을 때까지 기다리는 시간 (메일 폭탄 방지)
 */
@ConfigurationProperties("tspoon.user.password-reset")
public record PasswordResetProperties(
        @DefaultValue("30m") Duration tokenTtl,
        @DefaultValue("1m") Duration requestCooldown
) {
}
