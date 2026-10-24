-- 게시글별 댓글 목록(작성순) 조회용. board_id 단일 인덱스를 대신한다
CREATE INDEX idx_comments_board_id_created_at ON comments (board_id, created_at);
DROP INDEX idx_comments_board_id;
