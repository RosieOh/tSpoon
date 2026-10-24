package com.tspoon.communityservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.communityservice.dto.BoardRequest;
import com.tspoon.communityservice.dto.BoardResponse;
import com.tspoon.communityservice.entity.Board;
import com.tspoon.communityservice.service.BoardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    public ResponseEntity<PagedModel<BoardResponse>> getAllBoards(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size,
            @RequestParam(required = false) Board.BoardCategory category) {
        return ResponseEntity.ok(new PagedModel<>(boardService.getAllBoards(page, size, category)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BoardResponse> getBoardById(@PathVariable Long id) {
        BoardResponse board = boardService.getBoardById(id);
        return ResponseEntity.ok(board);
    }

    @PostMapping
    public ResponseEntity<BoardResponse> createBoard(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody BoardRequest request) {
        BoardResponse board = boardService.createBoard(authUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(board);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BoardResponse> updateBoard(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody BoardRequest request) {
        BoardResponse board = boardService.updateBoard(authUser, id, request);
        return ResponseEntity.ok(board);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBoard(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        boardService.deleteBoard(authUser, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> likeBoard(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        boardService.likeBoard(authUser, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlikeBoard(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        boardService.unlikeBoard(authUser, id);
        return ResponseEntity.ok().build();
    }
}
