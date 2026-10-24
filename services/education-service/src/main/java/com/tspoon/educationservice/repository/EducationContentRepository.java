package com.tspoon.educationservice.repository;

import com.tspoon.educationservice.entity.EducationContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
