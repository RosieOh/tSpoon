-- 회원 이름이 바뀌면 주최자 이름을 회원 ID 로 찾아 바꾼다 (UserEventHandler). 신청은 (user_id, status, created_at) 인덱스를 쓴다
CREATE INDEX idx_events_organizer_id ON events (organizer_id);
