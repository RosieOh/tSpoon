-- 생성·수정 등 시스템 시각을 시간대가 있는 timestamptz 로 바꾼다 (엔티티는 Instant).
-- 기존 값은 UTC 로 동작하는 컨테이너에서 기록됐으므로 UTC 로 해석한다.

ALTER TABLE education_contents
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE timestamptz(6) USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE content_bookmarks
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC';
