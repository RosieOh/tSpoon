-- 회원별 자녀 학년 (user-service 의 USER_CHILDREN_CHANGED 를 받아 만든 읽기 모델 — 맞춤 추천용)
-- 늦게 도착한 옛 이벤트로 덮어쓰지 않도록 이벤트 발생 시각을 함께 둔다
CREATE TABLE member_child_grades (
    user_id     bigint         NOT NULL,
    grades      varchar(30)[]  NOT NULL,
    occurred_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_member_child_grades PRIMARY KEY (user_id)
);
