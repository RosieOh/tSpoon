-- Transactional Outbox: 도메인 변경과 같은 트랜잭션에 기록하고, 릴레이가 Kafka 로 보낸 뒤 published_at 을 채운다
CREATE TABLE outbox_events (
    id             uuid         NOT NULL,
    topic          varchar(255) NOT NULL,
    aggregate_type varchar(50)  NOT NULL,
    aggregate_id   varchar(50)  NOT NULL,
    event_type     varchar(100) NOT NULL,
    payload        jsonb        NOT NULL,
    created_at     timestamptz(6) NOT NULL,
    published_at   timestamptz(6),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

-- 릴레이가 보낼 것만 빠르게 찾는다 (보낸 이벤트는 인덱스에서 빠짐)
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
