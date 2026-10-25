-- 대기자로 들어온 시각. 대기 순번은 이 순서(같으면 id 순)로 정한다
ALTER TABLE event_registrations ADD COLUMN waitlisted_at timestamptz(6);

-- 행사의 다음 대기자를 빠르게 찾는다 (대기 중인 신청만)
CREATE INDEX idx_event_registrations_waitlist ON event_registrations (event_id, waitlisted_at, id) WHERE status = 'WAITLISTED';
