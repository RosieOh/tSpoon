package com.tspoon.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 도메인 이벤트 소비자의 공통 설정.
 * <ul>
 *   <li>처리에 실패한 메시지는 1초 간격으로 3번 다시 시도하고, 그래도 안 되면 DLT("{토픽}.DLT")로 보내 뒤 메시지를 막지 않는다.
 *       JSON 이 깨졌거나 필드가 없는 메시지는 다시 해도 소용없으므로 바로 DLT 로 보낸다.</li>
 *   <li>{@link UserEventHandler} 가 있으면 회원 이벤트를 구독한다.</li>
 * </ul>
 */
@AutoConfiguration
public class DomainEventConsumerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler domainEventErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // DLT 파티션은 브로커가 정하게 한다 (원래 토픽과 파티션 수가 달라도 되도록)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3));
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(UserEventHandler.class)
    static class UserEventConsumerConfiguration {

        /**
         * 구독할 토픽을 기동할 때 먼저 만든다 (이미 있으면 그대로 둔다). 발행 서비스보다 먼저 떠서 없는 토픽을 구독하면
         * 메타데이터 갱신 주기(기본 5분)가 지나야 읽기 시작하기 때문이다.
         */
        @Bean
        public NewTopic userEventsTopic() {
            return TopicBuilder.name(TspoonTopics.USER_EVENTS).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
        }

        @Bean
        public UserEventListener userEventListener(List<UserEventHandler> handlers, JsonMapper jsonMapper) {
            return new UserEventListener(handlers, jsonMapper);
        }
    }
}
