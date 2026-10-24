package com.tspoon.apigateway.auth;

import com.tspoon.auth.AuthRedisKeys;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtProperties;
import com.tspoon.auth.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AccessTokenVerificationFilterTest {

    private static final String SECRET = "gateway-test-secret-key-0123456789abcdef";
    private static final AuthUser USER = new AuthUser(7L, "parent@tspoon.com", "김부모", "PARENT");

    private final JwtUtil jwtUtil = new JwtUtil(new JwtProperties(SECRET, "tspoon", Duration.ofMinutes(30), Duration.ofDays(14)));
    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    private final AccessTokenVerificationFilter filter = new AccessTokenVerificationFilter(
            jwtUtil, redis, new GatewayAuthProperties(List.of("/api/users/login")), JsonMapper.builder().build());

    private final AtomicBoolean passed = new AtomicBoolean();
    private final GatewayFilterChain chain = exchange -> {
        passed.set(true);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        given(redis.opsForValue()).willReturn(values);
        given(redis.hasKey(anyString())).willReturn(Mono.just(false));
        given(values.get(anyString())).willReturn(Mono.empty());
    }

    @Test
    void 토큰이_없는_요청은_그대로_넘긴다() {
        MockServerWebExchange exchange = exchange("/api/boards", null);

        filter.filter(exchange, chain).block();

        assertThat(passed).isTrue();
    }

    @Test
    void 유효한_토큰은_넘긴다() {
        MockServerWebExchange exchange = exchange("/api/users/profile", jwtUtil.generateAccessToken(USER));

        filter.filter(exchange, chain).block();

        assertThat(passed).isTrue();
    }

    @Test
    void 서명을_검증할_수_없는_토큰은_401() {
        MockServerWebExchange exchange = exchange("/api/users/profile", "1_1700000000000");

        filter.filter(exchange, chain).block();

        assertRejected(exchange, "토큰이 만료되었거나 유효하지 않습니다.");
    }

    @Test
    void 로그아웃해_차단_목록에_오른_토큰은_401() {
        String token = jwtUtil.generateAccessToken(USER);
        String tokenId = jwtUtil.verify(token).orElseThrow().tokenId();
        given(redis.hasKey(AuthRedisKeys.accessDenylist(tokenId))).willReturn(Mono.just(true));
        MockServerWebExchange exchange = exchange("/api/users/profile", token);

        filter.filter(exchange, chain).block();

        assertRejected(exchange, "로그아웃했거나");
    }

    @Test
    void 정지나_역할_변경_전에_발급된_토큰은_401() {
        given(values.get(AuthRedisKeys.tokenVersion(USER.userId()))).willReturn(Mono.just("1"));
        MockServerWebExchange exchange = exchange("/api/users/profile", jwtUtil.generateAccessToken(USER, 0));

        filter.filter(exchange, chain).block();

        assertRejected(exchange, "로그아웃했거나");
    }

    @Test
    void 현재_버전으로_발급된_토큰은_넘긴다() {
        given(values.get(AuthRedisKeys.tokenVersion(USER.userId()))).willReturn(Mono.just("1"));
        MockServerWebExchange exchange = exchange("/api/users/profile", jwtUtil.generateAccessToken(USER, 1));

        filter.filter(exchange, chain).block();

        assertThat(passed).isTrue();
    }

    @Test
    void 검증_제외_경로는_유효하지_않은_토큰이_붙어_있어도_넘긴다() {
        MockServerWebExchange exchange = exchange("/api/users/login", "expired-or-garbage");

        filter.filter(exchange, chain).block();

        assertThat(passed).isTrue();
    }

    @Test
    void Redis_장애_시에는_서명이_유효한_토큰을_넘긴다() {
        given(redis.hasKey(anyString())).willReturn(Mono.error(new IllegalStateException("redis down")));
        MockServerWebExchange exchange = exchange("/api/users/profile", jwtUtil.generateAccessToken(USER));

        filter.filter(exchange, chain).block();

        assertThat(passed).isTrue();
    }

    private void assertRejected(MockServerWebExchange exchange, String message) {
        assertThat(passed).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"success\":false").contains(message);
    }

    private static MockServerWebExchange exchange(String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return MockServerWebExchange.from(request);
    }
}
