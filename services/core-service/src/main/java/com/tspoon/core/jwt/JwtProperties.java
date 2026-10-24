package com.tspoon.core.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties("tspoon.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("tspoon") String issuer,
        @DefaultValue("24h") Duration accessTokenTtl
) {

    private static final int MIN_SECRET_BYTES = 32; // HS256 최소 키 길이 (256bit)

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "tspoon.jwt.secret 은 최소 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다. (환경변수 JWT_SECRET)");
        }
    }
}
