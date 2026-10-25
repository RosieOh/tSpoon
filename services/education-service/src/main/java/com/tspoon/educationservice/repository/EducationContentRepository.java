package com.tspoon.educationservice.repository;

import com.tspoon.educationservice.entity.EducationContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EducationContentRepository extends JpaRepository<EducationContent, Long> {

    // 카테고리·유형은 주어질 때만 거른다
    @Query("""
            SELECT c FROM EducationContent c
            WHERE c.status = :status
              AND (:category IS NULL OR c.category = :category)
              AND (:type IS NULL OR c.type = :type)
            ORDER BY c.createdAt DESC
            """)
    Page<EducationContent> search(@Param("status") EducationContent.ContentStatus status,
                                  @Param("category") EducationContent.ContentCategory category,
                                  @Param("type") EducationContent.ContentType type,
                                  Pageable pageable);

    // 맞춤 추천 — 자녀의 교육 단계 → 육아 → 그 밖의 순, 같은 순위는 인기·최신순. 이미 북마크한 것은 뺀다
    @Query("""
            SELECT c FROM EducationContent c
            WHERE c.status = com.tspoon.educationservice.entity.EducationContent.ContentStatus.ACTIVE
              AND NOT EXISTS (SELECT b.id FROM ContentBookmark b WHERE b.content = c AND b.userId = :userId)
            ORDER BY CASE WHEN c.category IN :stages THEN 0
                          WHEN c.category = com.tspoon.educationservice.entity.EducationContent.ContentCategory.PARENTING THEN 1
                          ELSE 2 END,
                     c.bookmarkCount DESC, c.viewCount DESC, c.createdAt DESC
            """)
    List<EducationContent> findRecommended(@Param("stages") Collection<EducationContent.ContentCategory> stages,
                                           @Param("userId") Long userId,
                                           Pageable pageable);

    // 인기순 — 로그인했으면 이미 북마크한 것은 뺀다
    @Query("""
            SELECT c FROM EducationContent c
            WHERE c.status = com.tspoon.educationservice.entity.EducationContent.ContentStatus.ACTIVE
              AND (:userId IS NULL OR NOT EXISTS (SELECT b.id FROM ContentBookmark b WHERE b.content = c AND b.userId = :userId))
            ORDER BY c.bookmarkCount DESC, c.viewCount DESC, c.createdAt DESC
            """)
    List<EducationContent> findPopular(@Param("userId") Long userId, Pageable pageable);

    // 카운터는 읽고-쓰기 대신 DB 에서 원자적으로 증감한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EducationContent c SET c.viewCount = c.viewCount + 1 WHERE c.id = :id")
    int incrementViewCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EducationContent c SET c.bookmarkCount = c.bookmarkCount + 1 WHERE c.id = :id")
    int incrementBookmarkCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EducationContent c SET c.bookmarkCount = c.bookmarkCount - 1 WHERE c.id = :id AND c.bookmarkCount > 0")
    int decrementBookmarkCount(@Param("id") Long id);
}
