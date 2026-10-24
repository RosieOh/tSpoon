package com.tspoon.qnaservice.dto;

import com.tspoon.qnaservice.entity.Question;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다")
        String title,

        @NotBlank(message = "내용은 필수입니다")
        String content,

        @NotNull(message = "카테고리는 필수입니다")
        Question.QuestionCategory category,

        Question.Priority priority,
        String tags,
        String attachments
) {
}
