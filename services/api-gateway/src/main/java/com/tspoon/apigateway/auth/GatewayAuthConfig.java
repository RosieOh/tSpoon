package com.tspoon.apigateway.auth;

import com.tspoon.auth.jwt.JwtProperties;
import com.tspoon.auth.jwt.JwtUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, GatewayAuthProperties.class})
public class GatewayAuthConfig {

    @Bean
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties);
    }

    @Bean
    public AccessTokenVerificationFilter accessTokenVerificationFilter(JwtUtil jwtUtil, ReactiveStringRedisTemplate redis,
                                                                       GatewayAuthProperties properties, JsonMapper jsonMapper) {
        return new AccessTokenVerificationFilter(jwtUtil, redis, properties, jsonMapper);
    }
}
