package com.tspoon.apigateway.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * @param skipPaths 토큰 검증을 건너뛸 경로. 로그인·재발급 요청에 만료된 Access Token 이 헤더로 따라와도 막지 않기 위함
 */
@ConfigurationProperties("tspoon.gateway.auth")
public record GatewayAuthProperties(List<String> skipPaths) {

    public GatewayAuthProperties {
        skipPaths = skipPaths == null ? List.of() : List.copyOf(skipPaths);
    }

    public boolean isSkipped(PathContainer path) {
        return skipPaths.stream()
                .map(PathPatternParser.defaultInstance::parse)
                .anyMatch((PathPattern pattern) -> pattern.matches(path));
    }
}
