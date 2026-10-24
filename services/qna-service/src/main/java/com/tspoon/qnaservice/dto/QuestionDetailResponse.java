package com.tspoon.qnaservice.dto;

import com.tspoon.qnaservice.entity.Question;

import java.time.Instant;
import java.util.List;

/**
 * @param answers 채택된 답변이 먼저, 나머지는 먼저 달린 순
 */
public record QuestionDetailResponse(
        Long id,
        String title,
        String content,
        Long authorId,
        String authorName,
        Question.QuestionCategory category,
        Question.QuestionStatus status,
        Question.Priority priority,
        String tags,
        String attachments,
        Integer viewCount,
        Integer answerCount,
        Long acceptedAnswerId,
        Instant createdAt,
        Instant updatedAt,
        List<AnswerResponse> answers
) {

    public static QuestionDetailResponse of(Question question, List<AnswerResponse> answers) {
        return new QuestionDetailResponse(
                question.getId(), question.getTitle(), question.getContent(), question.getAuthorId(),
                question.getAuthorName(), question.getCategory(), question.getStatus(), question.getPriority(),
                question.getTags(), question.getAttachments(), question.getViewCount(), question.getAnswerCount(),
                question.getAcceptedAnswerId(), question.getCreatedAt(), question.getUpdatedAt(), answers);
    }
}
