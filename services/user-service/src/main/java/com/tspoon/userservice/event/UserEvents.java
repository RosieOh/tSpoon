package com.tspoon.userservice.event;

import com.tspoon.event.TspoonTopics;
import com.tspoon.event.UserEventTypes;
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

    public static final String TOPIC = TspoonTopics.USER_EVENTS;
    public static final String AGGREGATE_USER = "USER";
    public static final String EMAIL_VERIFICATION_REQUESTED = UserEventTypes.EMAIL_VERIFICATION_REQUESTED;
    public static final String PASSWORD_RESET_REQUESTED = UserEventTypes.PASSWORD_RESET_REQUESTED;
    public static final String PASSWORD_CHANGED = UserEventTypes.PASSWORD_CHANGED;
    public static final String PROFILE_UPDATED = UserEventTypes.PROFILE_UPDATED;

    // 로컬·CI 는 브로커가 하나라 복제본 1
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }

    /**
     * 인증 메일 발송 요청. token 은 메일 링크에만 쓰는 일회용 값이다 — 쓰거나 만료(expiresAt)되면 쓸모가 없고,
     * user-service 는 해시만 보관한다.
     */
    public record EmailVerificationRequested(Long userId, String email, String name, String token, Instant expiresAt) {
    }

    /** 비밀번호 재설정 메일 발송 요청. token 은 EmailVerificationRequested 와 같은 성격의 일회용 값이다 */
    public record PasswordResetRequested(Long userId, String email, String name, String token, Instant expiresAt) {
    }

    /** 비밀번호가 바뀌었다 (재설정·변경 모두). 본인이 아닌 변경을 알아챌 수 있도록 메일로 알린다 */
    public record PasswordChanged(Long userId, String email, String name, Instant changedAt) {
    }

    /** 이름이 바뀌었다. 다른 서비스가 복사해 둔 작성자 이름을 바꾼다 */
    public record ProfileUpdated(Long userId, String name) {
    }
}
