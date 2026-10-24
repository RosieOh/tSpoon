package com.tspoon.qnaservice.repository;

import com.tspoon.qnaservice.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    // 카테고리는 주어질 때만 거른다
    @Query("""
            SELECT q FROM Question q
            WHERE q.status IN :statuses
              AND (:category IS NULL OR q.category = :category)
            ORDER BY q.createdAt DESC
            """)
    Page<Question> search(@Param("statuses") Collection<Question.QuestionStatus> statuses,
                          @Param("category") Question.QuestionCategory category,
                          Pageable pageable);

    // 채택은 질문당 한 번 — 이미 채택됐거나 열려 있지 않으면 0 을 돌려준다 (동시 채택 요청 중 하나만 성공)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Question q SET q.acceptedAnswerId = :answerId, q.status = com.tspoon.qnaservice.entity.Question.QuestionStatus.ANSWERED
            WHERE q.id = :id AND q.acceptedAnswerId IS NULL AND q.status = com.tspoon.qnaservice.entity.Question.QuestionStatus.OPEN
            """)
    int accept(@Param("id") Long id, @Param("answerId") Long answerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Question q SET q.viewCount = q.viewCount + 1 WHERE q.id = :id")
    int incrementViewCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Question q SET q.answerCount = q.answerCount + 1 WHERE q.id = :id")
    int incrementAnswerCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Question q SET q.answerCount = q.answerCount - 1 WHERE q.id = :id AND q.answerCount > 0")
    int decrementAnswerCount(@Param("id") Long id);
}
