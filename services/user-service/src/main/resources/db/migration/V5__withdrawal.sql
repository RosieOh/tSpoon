-- 탈퇴 시각. 탈퇴하면 개인정보를 지우고 상태를 WITHDRAWN 으로 바꾼다
ALTER TABLE users ADD COLUMN withdrawn_at timestamptz(6);
