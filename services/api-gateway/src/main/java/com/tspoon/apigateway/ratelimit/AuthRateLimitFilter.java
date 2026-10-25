package com.tspoon.apigateway.ratelimit;

import com.tspoon.apigateway.support.ProblemResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;

/**
 * 인증 관련 API(로그인·가입·재발급·메일 요청)를 클라이언트 IP 별로 제한한다.
 * 여러 계정을 돌아가며 비밀번호를 넣어 보는 공격, 대량 가입, 메일 폭탄을 막는다 (계정 하나에 대한 시도는 user-service 가 잠근다).
 *
 * 계산은 Spring Cloud Gateway 의 RedisRateLimiter(토큰 버킷, Redis Lua 스크립트로 원자적)가 하므로 Gateway 가 여러 대여도
 * 같은 한도를 나눠 쓴다. Redis 오류 시 RedisRateLimiter 는 통과시킨다 (토큰 폐기 확인과 같은 정책).
 */
@RequiredArgsConstructor
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    static final String LIMITER_ID = "auth";

    private final RateLimiter<?> rateLimiter;
    private final RemoteAddressResolver addressResolver;
    private final AuthRateLimitProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isLimited(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }
        InetSocketAddress client = addressResolver.resolve(exchange);
        if (client == null) {
            return chain.filter(exchange);
        }

        return rateLimiter.isAllowed(LIMITER_ID, clientKey(client)).flatMap(response -> {
            response.getHeaders().forEach(exchange.getResponse().getHeaders()::set);
            if (response.isAllowed()) {
                return chain.filter(exchange);
            }
            // 토큰은 초당 replenishRate 개씩 채워지므로 1초 뒤면 다시 보낼 수 있다
            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
            return ProblemResponses.write(exchange, jsonMapper, HttpStatus.TOO_MANY_REQUESTS,
                    "요청이 너무 많습니다. 잠시 뒤에 다시 시도해 주세요.", "RATE_LIMITED");
        });
    }

    private static String clientKey(InetSocketAddress client) {
        return client.getAddress() != null ? client.getAddress().getHostAddress() : client.getHostString();
    }

    // 토큰 검증보다 먼저 — 잘못된 토큰을 붙인 요청도 횟수에 넣는다
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
