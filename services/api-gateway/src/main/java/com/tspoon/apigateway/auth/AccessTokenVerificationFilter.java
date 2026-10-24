package com.tspoon.apigateway.auth;

import com.tspoon.auth.AuthRedisKeys;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.auth.jwt.VerifiedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bearer 토큰이 붙은 요청을 서비스로 보내기 전에 확인한다.
 * <ol>
 *   <li>서명·만료·발급자 — 실패하면 401</li>
 *   <li>로그아웃한 토큰(차단 목록), 정지·역할 변경 전에 발급된 토큰(토큰 버전) — 해당하면 401</li>
 * </ol>
 * 토큰이 없는 요청은 그대로 넘긴다. 공개 API 여부는 각 서비스의 인가 규칙이 정한다.
 *
 * 도메인 서비스는 외부에 노출되지 않으므로 폐기 여부는 여기서만 확인한다 (서비스는 서명만 다시 검증).
 */
@Slf4j
@RequiredArgsConstructor
public class AccessTokenVerificationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redis;
    private final GatewayAuthProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)
                || properties.isSkipped(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }

        Optional<VerifiedToken> verified = jwtUtil.verify(header.substring(BEARER_PREFIX.length()));
        if (verified.isEmpty()) {
            return reject(exchange, "토큰이 만료되었거나 유효하지 않습니다.");
        }

        return isRevoked(verified.get())
                .flatMap(revoked -> revoked
                        ? reject(exchange, "로그아웃했거나 더 이상 쓸 수 없는 토큰입니다. 다시 로그인해 주세요.")
                        : chain.filter(exchange));
    }

    private Mono<Boolean> isRevoked(VerifiedToken token) {
        Mono<Boolean> denylisted = redis.hasKey(AuthRedisKeys.accessDenylist(token.tokenId()));
        Mono<Long> currentVersion = redis.opsForValue()
                .get(AuthRedisKeys.tokenVersion(token.user().userId()))
                .map(Long::parseLong)
                .defaultIfEmpty(0L);

        return Mono.zip(denylisted, currentVersion)
                .map(result -> result.getT1() || token.version() < result.getT2())
                // Redis 장애로 모든 인증 요청이 막히지 않도록 통과시킨다. 서명·만료 검증은 이미 끝났고 Access Token 수명은 30분이다
                .onErrorResume(e -> {
                    log.warn("토큰 폐기 여부를 확인하지 못해 통과시킵니다 (Redis 오류): {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        // 도메인 서비스와 같은 RFC 9457 ProblemDetail 형식
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("detail", message);
        body.put("instance", exchange.getRequest().getPath().value());

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(body))));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
