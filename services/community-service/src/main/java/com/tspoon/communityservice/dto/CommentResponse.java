package com.tspoon.communityservice.dto;

import com.tspoon.communityservice.entity.Comment;

import java.time.Instant;
import java.util.List;

/**
 * 삭제된 댓글은 답글이 남아 있을 때만 자리를 지키며, 내용과 작성자는 내보내지 않는다.
 */
public record CommentResponse(
        Long id,
        Long parentCommentId,
        String content,
        Long authorId,
        String authorName,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> replies
) {

    private static final String DELETED_CONTENT = "삭제된 댓글입니다.";

    public static CommentResponse of(Comment comment, List<Comment> replies) {
        boolean deleted = comment.getStatus() != Comment.CommentStatus.ACTIVE;
        return new CommentResponse(
                comment.getId(),
                comment.getParentCommentId(),
                deleted ? DELETED_CONTENT : comment.getContent(),
                deleted ? null : comment.getAuthorId(),
                deleted ? null : comment.getAuthorName(),
                deleted,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replies.stream().map(reply -> of(reply, List.of())).toList());
    }
}
