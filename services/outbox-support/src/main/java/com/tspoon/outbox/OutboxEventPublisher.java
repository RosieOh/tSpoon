package com.tspoon.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * 도메인 이벤트를 outbox_events 에 기록한다. 반드시 도메인 변경과 같은 트랜잭션 안에서 호출해야 하며(MANDATORY),
 * 그래야 "저장은 됐는데 이벤트는 유실" 또는 "롤백됐는데 이벤트는 발행" 같은 불일치가 생기지 않는다.
 * Kafka 로 보내는 일은 {@link OutboxRelay} 가 따로 한다.
 */
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(String topic, String aggregateType, Object aggregateId, String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO outbox_events (id, topic, aggregate_type, aggregate_id, event_type, payload, created_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                        """,
                eventId, topic, aggregateType, String.valueOf(aggregateId), eventType,
                jsonMapper.writeValueAsString(payload), Timestamp.from(Instant.now()));
        return eventId;
    }
}
