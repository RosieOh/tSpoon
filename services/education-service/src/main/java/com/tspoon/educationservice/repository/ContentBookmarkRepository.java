package com.tspoon.educationservice.repository;

import com.tspoon.educationservice.entity.ContentBookmark;
import com.tspoon.educationservice.entity.EducationContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentBookmarkRepository extends JpaRepository<ContentBookmark, Long> {

    boolean existsByContentIdAndUserId(Long contentId, Long userId);

    long deleteByContentIdAndUserId(Long contentId, Long userId);

    // 공개 중인 콘텐츠의 북마크만, 최근에 담은 순
    @Query(value = """
            SELECT b FROM ContentBookmark b JOIN FETCH b.content c
            WHERE b.userId = :userId AND c.status = :status
            ORDER BY b.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(b) FROM ContentBookmark b
            WHERE b.userId = :userId AND b.content.status = :status
            """)
    Page<ContentBookmark> findMyBookmarks(@Param("userId") Long userId,
                                          @Param("status") EducationContent.ContentStatus status,
                                          Pageable pageable);
}
