package com.tspoon.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 회원 이벤트를 받아 서비스의 {@link UserEventHandler} 들에 넘긴다. 소비자 그룹은 서비스 이름이라
 * 서비스마다 모든 이벤트를 받고, 같은 서비스의 인스턴스끼리는 파티션을 나눠 받는다.
 */
@Slf4j
@RequiredArgsConstructor
public class UserEventListener {

    private final List<UserEventHandler> handlers;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = TspoonTopics.USER_EVENTS, groupId = "${spring.application.name}")
    public void onEvent(ConsumerRecord<String, String> record) {
        EventEnvelope event = EventEnvelope.parse(jsonMapper, record.value());
        JsonNode payload = event.payload();
        switch (event.eventType()) {
            case UserEventTypes.PROFILE_UPDATED -> {
                long userId = payload.required("userId").asLong();
                String name = payload.required("name").asString();
                handlers.forEach(handler -> handler.onProfileUpdated(userId, name));
            }
            case UserEventTypes.WITHDRAWN -> {
                long userId = payload.required("userId").asLong();
                handlers.forEach(handler -> handler.onWithdrawn(userId));
            }
            case UserEventTypes.CHILDREN_CHANGED -> {
                long userId = payload.required("userId").asLong();
                List<String> grades = payload.required("grades").valueStream().map(JsonNode::asString).toList();
                handlers.forEach(handler -> handler.onChildrenChanged(userId, grades, event.occurredAt()));
            }
            default -> log.trace("처리하지 않는 회원 이벤트 {} — 건너뜁니다", event.eventType());
        }
    }
}
