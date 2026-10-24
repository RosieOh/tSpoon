-- 내 알림함(최신순)과 안 읽은 개수 조회용
CREATE INDEX idx_notifications_user_id_channel_created_at ON notifications (user_id, channel, created_at DESC);
CREATE INDEX idx_notifications_user_id_channel_status ON notifications (user_id, channel, status);
