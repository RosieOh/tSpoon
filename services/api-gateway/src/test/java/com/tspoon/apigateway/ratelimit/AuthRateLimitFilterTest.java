package com.tspoon.apigateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthRateLimitFilterTest {

    private static final AuthRateLimitProperties PROPERTIES =
            new AuthRateLimitProperties(true, 2, 30, 0, List.of("/api/users/login", "/api/users/password-reset/**"));

    @SuppressWarnings("unchecked")
    private final RateLimiter<Object> rateLimiter = mock(RateLimiter.class);

    private final AtomicBoolean passed = new AtomicBoolean();
    private final GatewayFilterChain chain = exchange -> {
        passed.set(true);
        return Mono.empty();
    };

    @Test
    void 제한_경로가_아니면_세지_않는다() {
        filter(new RemoteAddressResolver() {
        }).filter(exchange("/api/boards", null), chain).block();

        assertThat(passed).isTrue();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void 허용되면_넘기고_남은_횟수를_헤더로_알린다() {
        allow(true);
        MockServerWebExchange exchange = exchange("/api/users/password-reset/request", null);

        filter(new RemoteAddressResolver() {
        }).filter(exchange, chain).block();

        assertThat(passed).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("7");
        verify(rateLimiter).isAllowed(AuthRateLimitFilter.LIMITER_ID, "203.0.113.7");
    }

    @Test
    void 한도를_넘으면_429_ProblemDetail과_Retry_After() {
        allow(false);
        MockServerWebExchange exchange = exchange("/api/users/login", null);

        filter(new RemoteAddressResolver() {
        }).filter(exchange, chain).block();

        assertThat(passed).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"status\":429").contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void 신뢰할_프록시가_없으면_위조할_수_있는_X_Forwarded_For_대신_접속_주소로_센다() {
        allow(true);

        filter(new RemoteAddressResolver() {
        }).filter(exchange("/api/users/login", "6.6.6.6"), chain).block();

        verify(rateLimiter).isAllowed(eq(AuthRateLimitFilter.LIMITER_ID), eq("203.0.113.7"));
    }

    @Test
    void 프록시_하나를_신뢰하면_프록시가_붙인_마지막_주소로_센다() {
        allow(true);

        filter(XForwardedRemoteAddressResolver.maxTrustedIndex(1))
                .filter(exchange("/api/users/login", "6.6.6.6, 198.51.100.9"), chain).block();

        verify(rateLimiter).isAllowed(eq(AuthRateLimitFilter.LIMITER_ID), eq("198.51.100.9"));
    }

    private AuthRateLimitFilter filter(RemoteAddressResolver resolver) {
        return new AuthRateLimitFilter(rateLimiter, resolver, PROPERTIES, JsonMapper.builder().build());
    }

    private void allow(boolean allowed) {
        given(rateLimiter.isAllowed(eq(AuthRateLimitFilter.LIMITER_ID), anyString()))
                .willReturn(Mono.just(new RateLimiter.Response(allowed, Map.of("X-RateLimit-Remaining", allowed ? "7" : "0"))));
    }

    private static MockServerWebExchange exchange(String path, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post(path)
                .remoteAddress(new InetSocketAddress("203.0.113.7", 50_000));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return MockServerWebExchange.from(request);
    }
}
