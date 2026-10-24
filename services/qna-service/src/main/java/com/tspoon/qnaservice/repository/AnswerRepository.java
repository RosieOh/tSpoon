package com.tspoon.qnaservice.repository;

import com.tspoon.qnaservice.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    // 채택된 답변을 먼저, 나머지는 먼저 달린 순
    List<Answer> findByQuestionIdAndStatusOrderByIsAcceptedDescCreatedAtAsc(Long questionId, Answer.AnswerStatus status);

    Optional<Answer> findByIdAndStatus(Long id, Answer.AnswerStatus status);
}
