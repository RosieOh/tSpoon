-- 이미 처리한 도메인 이벤트 (Kafka 는 최소 1회 전달이라 같은 이벤트가 다시 올 수 있다)
-- 알림 생성과 같은 트랜잭션에 기록해 이벤트당 알림을 한 번만 만든다
CREATE TABLE processed_events (
    event_id     uuid           NOT NULL,
    event_type   varchar(100)   NOT NULL,
    processed_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);
