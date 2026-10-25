package com.tspoon.apigateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * 인증 관련 API 의 클라이언트 IP 별 요청 제한 (토큰 버킷).
 *
 * @param replenishRate  초당 채워지는 요청 수 — 오래 두고 보면 이 속도까지 허용
 * @param burstCapacity  한꺼번에 몰아 쓸 수 있는 최대 요청 수
 * @param trustedProxies 앞단 프록시(로드밸런서·Ingress) 수. 0 이면 X-Forwarded-For 를 믿지 않고 접속 주소를 쓴다
 *                       (위조한 헤더로 제한을 피하지 못하게)
 * @param paths          제한할 경로 패턴
 */
@ConfigurationProperties("tspoon.gateway.rate-limit")
public record AuthRateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("2") int replenishRate,
        @DefaultValue("30") int burstCapacity,
        @DefaultValue("0") int trustedProxies,
        List<String> paths
) {

    public AuthRateLimitProperties {
        paths = paths == null ? List.of() : List.copyOf(paths);
    }

    public boolean isLimited(PathContainer path) {
        return paths.stream()
                .map(PathPatternParser.defaultInstance::parse)
                .anyMatch((PathPattern pattern) -> pattern.matches(path));
    }
}
