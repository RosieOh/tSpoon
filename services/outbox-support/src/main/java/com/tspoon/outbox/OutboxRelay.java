package com.tspoon.outbox;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 아직 보내지 않은 outbox 이벤트를 주기적으로 Kafka 로 보낸다 (최소 1회 전달 — 소비자는 eventId 로 중복을 걸러야 한다).
 *
 * FOR UPDATE SKIP LOCKED 로 가져오므로 서비스 인스턴스가 여러 개여도 같은 이벤트를 동시에 보내지 않는다.
 * Kafka 전송이 실패하면 트랜잭션을 롤백해 다음 주기에 다시 보낸다.
 */
@Slf4j
public class OutboxRelay {

    private static final String SELECT_PENDING = """
            SELECT id, topic, aggregate_type, aggregate_id, event_type, payload::text AS payload, created_at
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final OutboxProperties properties;

    public OutboxRelay(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                       KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper, OutboxProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${tspoon.outbox.relay-interval:1s}")
    public void relay() {
        try {
            Integer sent = transactionTemplate.execute(status -> relayBatch());
            if (sent != null && sent > 0) {
                log.debug("outbox 이벤트 {}건 발행", sent);
            }
        } catch (RuntimeException e) {
            log.warn("outbox 이벤트 발행 실패 — 다음 주기에 다시 시도합니다: {}", e.getMessage());
        }
    }

    private int relayBatch() {
        List<OutboxRow> rows = jdbcTemplate.query(SELECT_PENDING, (rs, i) -> new OutboxRow(
                rs.getObject("id", UUID.class), rs.getString("topic"), rs.getString("aggregate_type"),
                rs.getString("aggregate_id"), rs.getString("event_type"), rs.getString("payload"),
                rs.getTimestamp("created_at").toInstant()), properties.batchSize());

        for (OutboxRow row : rows) {
            ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.aggregateId(), envelope(row));
            record.headers().add("eventType", row.eventType().getBytes(StandardCharsets.UTF_8));
            try {
                kafkaTemplate.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Kafka 전송 실패: " + row.id(), e);
            }
            jdbcTemplate.update("UPDATE outbox_events SET published_at = ? WHERE id = ?", Timestamp.from(Instant.now()), row.id());
        }
        return rows.size();
    }

    // 소비자가 받는 메시지: {"eventId", "eventType", "occurredAt", "aggregateType", "aggregateId", "payload": {...}}
    private String envelope(OutboxRow row) {
        ObjectNode envelope = jsonMapper.createObjectNode();
        envelope.put("eventId", row.id().toString());
        envelope.put("eventType", row.eventType());
        envelope.put("occurredAt", row.createdAt().toString());
        envelope.put("aggregateType", row.aggregateType());
        envelope.put("aggregateId", row.aggregateId());
        envelope.set("payload", jsonMapper.readTree(row.payload()));
        return jsonMapper.writeValueAsString(envelope);
    }

    private record OutboxRow(UUID id, String topic, String aggregateType, String aggregateId, String eventType,
                             String payload, Instant createdAt) {
    }
}
