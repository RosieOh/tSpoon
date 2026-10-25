package com.tspoon.userservice.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Instant;

/**
 * user-service 가 발행하는 이벤트. 키는 회원 ID 라서 같은 회원의 이벤트는 같은 파티션에 순서대로 들어간다.
 */
@Configuration(proxyBeanMethods = false)
public class UserEvents {

    public static final String TOPIC = "tspoon.user.events";
    public static final String AGGREGATE_USER = "USER";
    public static final String EMAIL_VERIFICATION_REQUESTED = "EMAIL_VERIFICATION_REQUESTED";

    // 로컬·CI 는 브로커가 하나라 복제본 1
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }

    /**
     * 인증 메일 발송 요청. token 은 메일 링크에만 쓰는 일회용 값이다 — 쓰거나 만료(expiresAt)되면 쓸모가 없고,
     * user-service 는 해시만 보관한다.
     */
    public record EmailVerificationRequested(Long userId, String email, String name, String token, Instant expiresAt) {
    }
}
