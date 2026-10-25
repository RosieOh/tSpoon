package com.tspoon.apigateway;

import com.tspoon.apigateway.auth.AccessTokenVerificationFilter;
import com.tspoon.apigateway.ratelimit.AuthRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// Redis 는 연결을 처음 쓸 때 맺으므로 띄우지 않아도 된다 (빈 구성만 확인)
@SpringBootTest(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {

    @Autowired
    ApplicationContext context;

    @Test
    void 토큰_검증과_요청_제한_필터가_등록되고_요청_제한기는_하나만_있다() {
        assertThat(context.getBeansOfType(AccessTokenVerificationFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(AuthRateLimitFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(RateLimiter.class)).hasSize(1)
                .allSatisfy((name, limiter) -> assertThat(limiter).isInstanceOf(RedisRateLimiter.class));
    }
}
