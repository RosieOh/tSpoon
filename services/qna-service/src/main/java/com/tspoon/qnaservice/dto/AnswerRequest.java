package com.tspoon.qnaservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerRequest(
        @NotBlank(message = "답변 내용은 필수입니다")
        @Size(max = 5000, message = "답변은 5000자 이하여야 합니다")
        String content,

        String attachments
) {
}
