package com.tspoon.communityservice.dto;

import com.tspoon.communityservice.entity.Board;

import java.time.LocalDateTime;

public record BoardResponse(
        Long id,
        String title,
        String content,
        Long authorId,
        String authorName,
        Board.BoardCategory category,
        Board.BoardStatus status,
        String tags,
        String attachments,
        Integer viewCount,
        Integer likeCount,
        Integer commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BoardResponse from(Board board) {
        return new BoardResponse(
                board.getId(),
                board.getTitle(),
                board.getContent(),
                board.getAuthorId(),
                board.getAuthorName(),
                board.getCategory(),
                board.getStatus(),
                board.getTags(),
                board.getAttachments(),
                board.getViewCount(),
                board.getLikeCount(),
                board.getCommentCount(),
                board.getCreatedAt(),
                board.getUpdatedAt());
    }
}
