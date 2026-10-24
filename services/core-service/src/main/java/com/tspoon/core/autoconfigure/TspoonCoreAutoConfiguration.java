package com.tspoon.core.autoconfigure;

import com.tspoon.auth.jwt.JwtProperties;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.exception.GlobalExceptionHandler;
import com.tspoon.core.openapi.OpenApiConfig;
import com.tspoon.core.security.SecurityConfig;
import com.tspoon.core.security.TspoonSecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * core-service 를 의존성으로 추가하기만 하면 JWT 인증·Security 설정·공통 예외 처리가 적용된다.
 * (각 서비스의 @SpringBootApplication 패키지 스캔 범위 밖이므로 Auto-Configuration 으로 등록한다)
 *
 * Boot 기본 SecurityFilterChain 보다 먼저 등록되어야 하므로 ServletWebSecurityAutoConfiguration 이전에 처리한다.
 */
@AutoConfiguration(before = ServletWebSecurityAutoConfiguration.class)
@EnableConfigurationProperties({JwtProperties.class, TspoonSecurityProperties.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, OpenApiConfig.class})
public class TspoonCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties);
    }
}
