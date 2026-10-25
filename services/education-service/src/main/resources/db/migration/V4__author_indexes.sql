-- 회원 이름이 바뀌면 작성자 이름을 회원 ID 로 찾아 바꾼다 (UserEventHandler). 북마크는 (user_id, created_at) 인덱스를 쓴다
CREATE INDEX idx_education_contents_author_id ON education_contents (author_id);
