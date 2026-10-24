package com.tspoon.communityservice.dto;

import com.tspoon.communityservice.entity.Board;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BoardRequest(
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다")
        String title,

        @NotBlank(message = "내용은 필수입니다")
        String content,

        @NotNull(message = "카테고리는 필수입니다")
        Board.BoardCategory category,

        String tags,
        String attachments
) {
}
