package com.tspoon.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 서비스별 인증 없이 접근 가능한 경로.
 *
 * <pre>
 * tspoon:
 *   security:
 *     public-paths: [/api/users/register, /api/users/login]   # 모든 메서드 허용
 *     public-get-paths: [/api/boards/**]                       # GET 만 허용
 * </pre>
 */
@ConfigurationProperties("tspoon.security")
public record TspoonSecurityProperties(List<String> publicPaths, List<String> publicGetPaths) {

    public TspoonSecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        publicGetPaths = publicGetPaths == null ? List.of() : List.copyOf(publicGetPaths);
    }
}
