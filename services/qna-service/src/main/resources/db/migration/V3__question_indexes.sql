-- 질문 목록(최신순, 카테고리 필터)과 질문별 답변 목록(채택 먼저, 작성순) 조회용
CREATE INDEX idx_questions_status_created_at ON questions (status, created_at DESC);
CREATE INDEX idx_questions_category_status_created_at ON questions (category, status, created_at DESC);
CREATE INDEX idx_answers_question_id_created_at ON answers (question_id, created_at);
DROP INDEX idx_answers_question_id;
