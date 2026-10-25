package com.tspoon.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param tokenTtl            인증 링크 유효 시간
 * @param resendCooldown      인증 메일을 다시 보낼 수 있을 때까지 기다리는 시간 (메일 폭탄 방지)
 * @param unverifiedRetention 인증하지 않은 가입을 지우기까지의 기간 — 남의 이메일을 선점해 두지 못하게
 */
@ConfigurationProperties("tspoon.user.email-verification")
public record EmailVerificationProperties(
        @DefaultValue("24h") Duration tokenTtl,
        @DefaultValue("1m") Duration resendCooldown,
        @DefaultValue("7d") Duration unverifiedRetention
) {
}
