package com.tspoon.communityservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param parentCommentId 답글이면 부모 댓글 ID. 답글에는 다시 답글을 달 수 없다 (한 단계만)
 */
public record CommentRequest(
        @NotBlank(message = "댓글 내용은 필수입니다")
        @Size(max = 2000, message = "댓글은 2000자 이하여야 합니다")
        String content,

        Long parentCommentId
) {
}
