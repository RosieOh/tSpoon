package com.tspoon.notificationservice.event;

import com.tspoon.event.TspoonTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// 처리에 실패한 메시지의 재시도·DLT 는 event-support 의 공통 오류 처리기가 맡는다
@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {

    /**
     * 구독할 토픽을 기동할 때 먼저 만든다 (이미 있으면 그대로 둔다).
     * 소비자가 구독을 시작할 때 토픽이 아직 없으면 메타데이터 갱신 주기(기본 5분)가 지나야 그 토픽을 읽기 시작하므로,
     * 발행 서비스보다 먼저 뜨면 그동안의 알림이 늦어진다. 파티션·복제본 수는 발행 서비스의 선언과 같게 둔다.
     */
    @Bean
    public NewTopic communityEventsTopic() {
        return TopicBuilder.name(DomainEventListener.COMMUNITY_TOPIC).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic qnaEventsTopic() {
        return TopicBuilder.name(DomainEventListener.QNA_TOPIC).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(DomainEventListener.USER_TOPIC).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }
}
