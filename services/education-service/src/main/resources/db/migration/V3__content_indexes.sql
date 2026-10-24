-- 공개 콘텐츠 목록(최신순, 카테고리 필터)과 내 북마크 목록(최근에 담은 순) 조회용
CREATE INDEX idx_education_contents_status_created_at ON education_contents (status, created_at DESC);
CREATE INDEX idx_education_contents_category_status_created_at ON education_contents (category, status, created_at DESC);
CREATE INDEX idx_content_bookmarks_user_id_created_at ON content_bookmarks (user_id, created_at DESC);
