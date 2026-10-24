package com.tspoon.communityservice.service;

import com.tspoon.auth.AuthUser;
import com.tspoon.communityservice.dto.BoardRequest;
import com.tspoon.communityservice.dto.BoardResponse;
import com.tspoon.communityservice.entity.Board;
import com.tspoon.communityservice.entity.BoardLike;
import com.tspoon.communityservice.repository.BoardLikeRepository;
import com.tspoon.communityservice.repository.BoardRepository;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional
public class BoardService {

    private final BoardRepository boardRepository;
    private final BoardLikeRepository boardLikeRepository;

    @Transactional(readOnly = true)
    public Page<BoardResponse> getAllBoards(int page, int size, Board.BoardCategory category) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Board> boards;

        if (category != null) {
            boards = boardRepository.findByCategoryAndStatusOrderByCreatedAtDesc(
                    category, Board.BoardStatus.ACTIVE, pageable);
        } else {
            boards = boardRepository.findByStatusOrderByCreatedAtDesc(
                    Board.BoardStatus.ACTIVE, pageable);
        }

        return boards.map(BoardResponse::from);
    }

    // 조회수 증가(쓰기)가 있으므로 readOnly 트랜잭션이면 안 된다
    public BoardResponse getBoardById(Long id) {
        if (boardRepository.incrementViewCount(id, Board.BoardStatus.ACTIVE) == 0) {
            throw new ResourceNotFoundException("게시글을 찾을 수 없습니다.");
        }
        return BoardResponse.from(findActiveBoard(id));
    }

    public BoardResponse createBoard(AuthUser authUser, BoardRequest request) {
        Board board = Board.builder()
                .title(request.title())
                .content(request.content())
                .authorId(authUser.userId())
                .authorName(authUser.name()) // JWT 에 담긴 이름 (작성 시점 스냅샷)
                .category(request.category())
                .tags(request.tags())
                .attachments(request.attachments())
                .status(Board.BoardStatus.ACTIVE)
                .build();

        return BoardResponse.from(boardRepository.save(board));
    }

    public BoardResponse updateBoard(AuthUser authUser, Long id, BoardRequest request) {
        Board board = findActiveBoard(id);
        verifyAuthor(board, authUser, "게시글을 수정할 권한이 없습니다.");

        board.setTitle(request.title());
        board.setContent(request.content());
        board.setCategory(request.category());
        board.setTags(request.tags());
        board.setAttachments(request.attachments());

        return BoardResponse.from(boardRepository.saveAndFlush(board));
    }

    public void deleteBoard(AuthUser authUser, Long id) {
        Board board = findActiveBoard(id);
        verifyAuthor(board, authUser, "게시글을 삭제할 권한이 없습니다.");

        board.setStatus(Board.BoardStatus.DELETED);
    }

    public void likeBoard(AuthUser authUser, Long id) {
        Board board = findActiveBoard(id);

        if (boardLikeRepository.existsByBoardIdAndUserId(id, authUser.userId())) {
            throw new BusinessException("이미 좋아요를 누른 게시글입니다.", HttpStatus.CONFLICT);
        }

        // 동시 요청은 (board_id, user_id) 유니크 제약이 막는다 → GlobalExceptionHandler 에서 409
        boardLikeRepository.saveAndFlush(BoardLike.builder()
                .board(board)
                .userId(authUser.userId())
                .userName(authUser.name())
                .build());
        boardRepository.incrementLikeCount(id);
    }

    public void unlikeBoard(AuthUser authUser, Long id) {
        findActiveBoard(id);

        if (boardLikeRepository.deleteByBoardIdAndUserId(id, authUser.userId()) == 0) {
            throw new ResourceNotFoundException("좋아요를 찾을 수 없습니다.");
        }
        boardRepository.decrementLikeCount(id);
    }

    private Board findActiveBoard(Long id) {
        return boardRepository.findByIdAndStatus(id, Board.BoardStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("게시글을 찾을 수 없습니다."));
    }

    private void verifyAuthor(Board board, AuthUser authUser, String message) {
        if (!board.getAuthorId().equals(authUser.userId())) {
            throw new BusinessException(message, HttpStatus.FORBIDDEN);
        }
    }
}
