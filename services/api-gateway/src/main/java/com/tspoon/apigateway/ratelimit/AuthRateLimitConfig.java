package com.tspoon.apigateway.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthRateLimitProperties.class)
@ConditionalOnProperty(prefix = "tspoon.gateway.rate-limit", name = "enabled", matchIfMissing = true)
public class AuthRateLimitConfig {

    // 이 빈이 있으면 Gateway 자동 설정의 기본 RedisRateLimiter 는 만들어지지 않는다
    @Bean
    public RedisRateLimiter authRateLimiter(AuthRateLimitProperties properties) {
        return new RedisRateLimiter(properties.replenishRate(), properties.burstCapacity(), 1);
    }

    @Bean
    public AuthRateLimitFilter authRateLimitFilter(RedisRateLimiter authRateLimiter, AuthRateLimitProperties properties,
                                                   JsonMapper jsonMapper) {
        RemoteAddressResolver resolver = properties.trustedProxies() > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(properties.trustedProxies())
                : new RemoteAddressResolver() {
                };
        return new AuthRateLimitFilter(authRateLimiter, resolver, properties, jsonMapper);
    }
}
