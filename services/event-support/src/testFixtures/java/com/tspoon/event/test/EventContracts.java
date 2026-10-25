package com.tspoon.event.test;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이벤트 계약(JSON Schema) 검증. 발행 측 테스트는 실제로 낸 메시지를, 소비 측 테스트는 보내는 픽스처를 같은 스키마로 확인한다.
 * 그래야 발행 측이 필드를 빼거나 바꾸면 발행 측 테스트가 먼저 실패한다 (소비 측 픽스처만 보면 알아채지 못한다).
 * 스키마는 event-support 의 {@code src/main/resources/event-schemas/{eventType}.schema.json} 이다.
 */
public final class EventContracts {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final Map<String, Schema> SCHEMAS = new ConcurrentHashMap<>();

    private EventContracts() {
    }

    /** Kafka 로 오가는 메시지(봉투 + payload)가 계약에 맞는지 확인한다 */
    public static void assertConforms(String message) {
        JsonNode envelope = JSON.readTree(message);
        assertValid("envelope", envelope);
        assertValid(envelope.required("eventType").asString(), envelope.required("payload"));
    }

    /** outbox 에 기록한 payload 가 계약에 맞는지 확인한다 */
    public static void assertPayloadConforms(String eventType, String payloadJson) {
        assertValid(eventType, JSON.readTree(payloadJson));
    }

    /** 계약을 지킨 메시지를 만든다 — 소비 측 테스트의 픽스처. 계약에 어긋나면 만들지 않는다 */
    public static String envelope(String eventType, String aggregateType, Object aggregateId, String payloadJson) {
        String message = """
                {"eventId": "%s", "eventType": "%s", "occurredAt": "%s", "aggregateType": "%s", "aggregateId": "%s", "payload": %s}
                """.formatted(UUID.randomUUID(), eventType, Instant.now(), aggregateType, aggregateId, payloadJson);
        assertConforms(message);
        return message;
    }

    private static void assertValid(String schemaName, JsonNode node) {
        List<com.networknt.schema.Error> errors = SCHEMAS.computeIfAbsent(schemaName, EventContracts::load)
                .validate(node, context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
        if (!errors.isEmpty()) {
            throw new AssertionError(schemaName + " 계약에 맞지 않습니다: " + errors + "\n" + node);
        }
    }

    private static Schema load(String schemaName) {
        String path = "event-schemas/" + schemaName + ".schema.json";
        InputStream schema = EventContracts.class.getClassLoader().getResourceAsStream(path);
        if (schema == null) {
            throw new AssertionError("계약(스키마)이 없는 이벤트입니다: " + schemaName + " — " + path + " 를 추가하세요.");
        }
        try (schema) {
            return REGISTRY.getSchema(schema);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
