package com.tspoon.qnaservice.dto;

import com.tspoon.qnaservice.entity.Question;

import java.time.Instant;

public record QuestionSummary(
        Long id,
        String title,
        String authorName,
        Question.QuestionCategory category,
        Question.QuestionStatus status,
        Question.Priority priority,
        Integer viewCount,
        Integer answerCount,
        boolean accepted,
        Instant createdAt
) {

    public static QuestionSummary from(Question question) {
        return new QuestionSummary(
                question.getId(), question.getTitle(), question.getAuthorName(), question.getCategory(),
                question.getStatus(), question.getPriority(), question.getViewCount(), question.getAnswerCount(),
                question.getAcceptedAnswerId() != null, question.getCreatedAt());
    }
}
