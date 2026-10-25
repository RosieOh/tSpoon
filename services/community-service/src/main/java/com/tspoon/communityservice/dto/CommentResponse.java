package com.tspoon.communityservice.dto;

import com.tspoon.communityservice.entity.Comment;

import java.time.Instant;
import java.util.List;

/**
 * 삭제되거나 숨겨진 댓글은 답글이 남아 있을 때만 자리를 지키며, 내용과 작성자는 내보내지 않는다.
 *
 * @param deleted 공개 중이 아님 (삭제 또는 숨김)
 * @param hidden  신고·운영 정책으로 숨겨짐
 */
public record CommentResponse(
        Long id,
        Long parentCommentId,
        String content,
        Long authorId,
        String authorName,
        boolean deleted,
        boolean hidden,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> replies
) {

    private static final String DELETED_CONTENT = "삭제된 댓글입니다.";
    private static final String HIDDEN_CONTENT = "운영 정책에 따라 숨겨진 댓글입니다.";

    public static CommentResponse of(Comment comment, List<Comment> replies) {
        boolean deleted = comment.getStatus() != Comment.CommentStatus.ACTIVE;
        boolean hidden = comment.getStatus() == Comment.CommentStatus.HIDDEN;
        return new CommentResponse(
                comment.getId(),
                comment.getParentCommentId(),
                hidden ? HIDDEN_CONTENT : deleted ? DELETED_CONTENT : comment.getContent(),
                deleted ? null : comment.getAuthorId(),
                deleted ? null : comment.getAuthorName(),
                deleted,
                hidden,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replies.stream().map(reply -> of(reply, List.of())).toList());
    }
}
