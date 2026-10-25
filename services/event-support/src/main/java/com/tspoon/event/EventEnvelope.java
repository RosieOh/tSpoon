package com.tspoon.event;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * outbox 릴레이가 보내는 메시지 봉투.
 * <pre>
 * {"eventId": "…", "eventType": "USER_PROFILE_UPDATED", "occurredAt": "…", "aggregateType": "USER", "aggregateId": "7", "payload": {…}}
 * </pre>
 * 필수 항목이 없거나 JSON 이 깨졌으면 예외를 던진다 — 다시 해도 소용없으므로 소비자 오류 처리기가 바로 DLT 로 보낸다.
 */
public record EventEnvelope(UUID eventId, String eventType, Instant occurredAt, String aggregateType, String aggregateId,
                            JsonNode payload) {

    public static EventEnvelope parse(JsonMapper jsonMapper, String message) {
        JsonNode node = jsonMapper.readTree(message);
        return new EventEnvelope(
                UUID.fromString(node.required("eventId").asString()),
                node.required("eventType").asString(),
                Instant.parse(node.required("occurredAt").asString()),
                node.required("aggregateType").asString(),
                node.required("aggregateId").asString(),
                node.required("payload"));
    }
}
