package com.tspoon.communityservice.service;

import com.tspoon.communityservice.dto.BoardRequest;
import com.tspoon.communityservice.dto.BoardResponse;
import com.tspoon.communityservice.entity.Board;
import com.tspoon.communityservice.entity.BoardLike;
import com.tspoon.communityservice.repository.BoardLikeRepository;
import com.tspoon.communityservice.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BoardService {
    
    private final BoardRepository boardRepository;
    private final BoardLikeRepository boardLikeRepository;
    
    @Transactional(readOnly = true)
    public List<BoardResponse> getAllBoards(int page, int size, String category) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Board> boards;
        
        if (category != null && !category.isEmpty()) {
            Board.BoardCategory boardCategory = Board.BoardCategory.valueOf(category.toUpperCase());
            boards = boardRepository.findByCategoryAndStatusOrderByCreatedAtDesc(
                    boardCategory, Board.BoardStatus.ACTIVE, pageable);
        } else {
            boards = boardRepository.findByStatusOrderByCreatedAtDesc(
                    Board.BoardStatus.ACTIVE, pageable);
        }
        
        return boards.getContent().stream()
                .map(BoardResponse::from)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public BoardResponse getBoardById(Long id) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        
        // 조회수 증가
        board.setViewCount(board.getViewCount() + 1);
        boardRepository.save(board);
        
        return BoardResponse.from(board);
    }
    
    public BoardResponse createBoard(String token, BoardRequest request) {
        Long userId = extractUserIdFromToken(token);
        String userName = "사용자"; // 실제로는 User Service에서 조회
        
        Board board = Board.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .authorId(userId)
                .authorName(userName)
                .category(request.getCategory())
                .tags(request.getTags())
                .attachments(request.getAttachments())
                .status(Board.BoardStatus.ACTIVE)
                .build();
        
        Board savedBoard = boardRepository.save(board);
        return BoardResponse.from(savedBoard);
    }
    
    public BoardResponse updateBoard(String token, Long id, BoardRequest request) {
        Long userId = extractUserIdFromToken(token);
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        
        if (!board.getAuthorId().equals(userId)) {
            throw new RuntimeException("게시글을 수정할 권한이 없습니다.");
        }
        
        board.setTitle(request.getTitle());
        board.setContent(request.getContent());
        board.setCategory(request.getCategory());
        board.setTags(request.getTags());
        board.setAttachments(request.getAttachments());
        
        Board updatedBoard = boardRepository.save(board);
        return BoardResponse.from(updatedBoard);
    }
    
    public void deleteBoard(String token, Long id) {
        Long userId = extractUserIdFromToken(token);
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        
        if (!board.getAuthorId().equals(userId)) {
            throw new RuntimeException("게시글을 삭제할 권한이 없습니다.");
        }
        
        board.setStatus(Board.BoardStatus.DELETED);
        boardRepository.save(board);
    }
    
    public void likeBoard(String token, Long id) {
        Long userId = extractUserIdFromToken(token);
        String userName = "사용자"; // 실제로는 User Service에서 조회
        
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        
        if (boardLikeRepository.existsByBoardIdAndUserId(id, userId)) {
            throw new RuntimeException("이미 좋아요를 누른 게시글입니다.");
        }
        
        BoardLike boardLike = BoardLike.builder()
                .board(board)
                .userId(userId)
                .userName(userName)
                .build();
        
        boardLikeRepository.save(boardLike);
        
        // 좋아요 수 증가
        board.setLikeCount(board.getLikeCount() + 1);
        boardRepository.save(board);
    }
    
    public void unlikeBoard(String token, Long id) {
        Long userId = extractUserIdFromToken(token);
        
        BoardLike boardLike = boardLikeRepository.findByBoardIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("좋아요를 찾을 수 없습니다."));
        
        boardLikeRepository.delete(boardLike);
        
        // 좋아요 수 감소
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        board.setLikeCount(Math.max(0, board.getLikeCount() - 1));
        boardRepository.save(board);
    }
    
    private Long extractUserIdFromToken(String token) {
        // 실제 구현에서는 JWT 토큰을 파싱하여 사용자 ID 추출
        String cleanToken = token.replace("Bearer ", "");
        String[] parts = cleanToken.split("_");
        return Long.parseLong(parts[0]);
    }
}
