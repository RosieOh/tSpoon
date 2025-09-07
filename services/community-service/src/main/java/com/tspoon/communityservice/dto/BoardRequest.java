package com.tspoon.communityservice.dto;

import com.tspoon.communityservice.entity.Board;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BoardRequest {
    
    @NotBlank(message = "제목은 필수입니다")
    private String title;
    
    @NotBlank(message = "내용은 필수입니다")
    private String content;
    
    @NotNull(message = "카테고리는 필수입니다")
    private Board.BoardCategory category;
    
    private String tags;
    private String attachments;
}
