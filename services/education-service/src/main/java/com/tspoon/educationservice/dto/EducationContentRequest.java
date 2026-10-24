package com.tspoon.educationservice.dto;

import com.tspoon.educationservice.entity.EducationContent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param draft true 면 임시저장(DRAFT) — 작성자와 관리자만 볼 수 있다
 */
public record EducationContentRequest(
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다")
        String title,

        @NotBlank(message = "설명은 필수입니다")
        String description,

        String content,

        @NotNull(message = "콘텐츠 유형은 필수입니다")
        EducationContent.ContentType type,

        @NotNull(message = "카테고리는 필수입니다")
        EducationContent.ContentCategory category,

        String tags,
        String attachments,
        String thumbnailUrl,

        @Min(value = 1, message = "예상 학습 시간은 1분 이상이어야 합니다")
        Integer estimatedTime,

        String targetAge,
        boolean draft
) {
}
