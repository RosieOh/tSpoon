package com.tspoon.communityservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.communityservice.dto.CommentRequest;
import com.tspoon.communityservice.dto.CommentResponse;
import com.tspoon.communityservice.dto.CommentUpdateRequest;
import com.tspoon.communityservice.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "댓글", description = "게시글 댓글·답글 (답글은 한 단계)")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "게시글 댓글 목록 — 최상위 댓글 아래에 답글")
    @SecurityRequirements
    @GetMapping("/api/boards/{boardId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long boardId) {
        return ResponseEntity.ok(commentService.getComments(boardId));
    }

    @Operation(summary = "댓글·답글 작성 — 답글이면 parentCommentId")
    @PostMapping("/api/boards/{boardId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long boardId,
            @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(authUser, boardId, request));
    }

    @Operation(summary = "댓글 수정 (작성자만)")
    @PutMapping("/api/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        return ResponseEntity.ok(commentService.update(authUser, commentId, request));
    }

    @Operation(summary = "댓글 삭제 (작성자·관리자)")
    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long commentId) {
        commentService.delete(authUser, commentId);
        return ResponseEntity.noContent().build();
    }
}
