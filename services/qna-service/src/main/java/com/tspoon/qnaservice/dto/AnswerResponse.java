package com.tspoon.qnaservice.dto;

import com.tspoon.qnaservice.entity.Answer;

import java.time.Instant;

/**
 * @param type 답변자의 역할로 정해진 답변 유형 (EXPERT · TEACHER · PARENT · GENERAL)
 */
public record AnswerResponse(
        Long id,
        Long questionId,
        String content,
        Long authorId,
        String authorName,
        Answer.AnswerType type,
        String attachments,
        boolean accepted,
        Instant createdAt,
        Instant updatedAt
) {

    public static AnswerResponse from(Answer answer) {
        return new AnswerResponse(
                answer.getId(), answer.getQuestion().getId(), answer.getContent(), answer.getAuthorId(),
                answer.getAuthorName(), answer.getType(), answer.getAttachments(), Boolean.TRUE.equals(answer.getIsAccepted()),
                answer.getCreatedAt(), answer.getUpdatedAt());
    }
}
