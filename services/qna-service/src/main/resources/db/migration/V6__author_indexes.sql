-- 회원 이름이 바뀌면 작성자 이름을 회원 ID 로 찾아 바꾼다 (UserEventHandler)
CREATE INDEX idx_questions_author_id ON questions (author_id);
CREATE INDEX idx_answers_author_id ON answers (author_id);
