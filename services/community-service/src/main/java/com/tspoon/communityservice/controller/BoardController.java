package com.tspoon.communityservice.controller;

import com.tspoon.communityservice.dto.BoardRequest;
import com.tspoon.communityservice.dto.BoardResponse;
import com.tspoon.communityservice.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {
    
    private final BoardService boardService;
    
    @GetMapping
    public ResponseEntity<List<BoardResponse>> getAllBoards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        List<BoardResponse> boards = boardService.getAllBoards(page, size, category);
        return ResponseEntity.ok(boards);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<BoardResponse> getBoardById(@PathVariable Long id) {
        BoardResponse board = boardService.getBoardById(id);
        return ResponseEntity.ok(board);
    }
    
    @PostMapping
    public ResponseEntity<BoardResponse> createBoard(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody BoardRequest request) {
        BoardResponse board = boardService.createBoard(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(board);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<BoardResponse> updateBoard(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @Valid @RequestBody BoardRequest request) {
        BoardResponse board = boardService.updateBoard(token, id, request);
        return ResponseEntity.ok(board);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBoard(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        boardService.deleteBoard(token, id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> likeBoard(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        boardService.likeBoard(token, id);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlikeBoard(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        boardService.unlikeBoard(token, id);
        return ResponseEntity.ok().build();
    }
}
