-- 회원 이름이 바뀌면 작성자 이름을 회원 ID 로 찾아 바꾼다 (UserEventHandler)
CREATE INDEX idx_boards_author_id ON boards (author_id);
CREATE INDEX idx_comments_author_id ON comments (author_id);
CREATE INDEX idx_board_likes_user_id ON board_likes (user_id);
