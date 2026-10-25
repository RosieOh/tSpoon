package com.tspoon.notificationservice.event;

import com.tspoon.event.TspoonTopics;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * community·qna·user 가 outbox 로 발행한 이벤트를 받는다. 처리 중 예외가 나면 {@link KafkaConsumerConfig} 의 규칙대로
 * 재시도한 뒤 DLT(원래 토픽 + ".DLT")로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class DomainEventListener {

    public static final String COMMUNITY_TOPIC = TspoonTopics.COMMUNITY_EVENTS;
    public static final String QNA_TOPIC = TspoonTopics.QNA_EVENTS;
    public static final String USER_TOPIC = TspoonTopics.USER_EVENTS;

    private final DomainEventNotifier notifier;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = {COMMUNITY_TOPIC, QNA_TOPIC, USER_TOPIC}, groupId = "notification-service")
    public void onEvent(ConsumerRecord<String, String> record) {
        notifier.handle(jsonMapper.readTree(record.value()));
    }
}
