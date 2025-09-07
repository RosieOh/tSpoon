package com.tspoon.communityservice.dto;

import com.tspoon.communityservice.entity.Board;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BoardResponse {
    
    private Long id;
    private String title;
    private String content;
    private Long authorId;
    private String authorName;
    private Board.BoardCategory category;
    private Board.BoardStatus status;
    private String tags;
    private String attachments;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static BoardResponse from(Board board) {
        return BoardResponse.builder()
                .id(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .authorId(board.getAuthorId())
                .authorName(board.getAuthorName())
                .category(board.getCategory())
                .status(board.getStatus())
                .tags(board.getTags())
                .attachments(board.getAttachments())
                .viewCount(board.getViewCount())
                .likeCount(board.getLikeCount())
                .commentCount(board.getCommentCount())
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .build();
    }
}
