-- 행사 목록(다가오는 순)과 내 신청 목록 조회용
CREATE INDEX idx_events_status_start_date ON events (status, start_date);
CREATE INDEX idx_event_registrations_user_id_status_created_at ON event_registrations (user_id, status, created_at DESC);
